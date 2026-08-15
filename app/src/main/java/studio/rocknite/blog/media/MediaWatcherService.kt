package studio.rocknite.blog.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.rocknite.blog.data.LastDetectionStore
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.data.TrackedPackagesCache
import studio.rocknite.blog.network.ApiClient
import java.io.ByteArrayOutputStream

/**
 * Écoute les MediaSessions actives du système (fonctionne même en fullscreen / écran verrouillé,
 * car c'est un mécanisme système indépendant de l'UI de l'appli source).
 *
 * La liste des apps suivies (package_name) et les messages affichés sont entièrement configurables
 * depuis l'app (écran "Apps suivies") et stockés côté serveur — ce service ne fait que pousser les
 * données brutes (package détecté, titre, artiste/épisode, pochette si dispo) vers /api/now-playing.
 * Le site choisit le message à afficher au hasard parmi les templates configurés pour ce package.
 *
 * Deux mécanismes combinés pour la détection, plus fiables que l'écoute seule :
 *  - OnActiveSessionsChangedListener : réaction rapide quand une session démarre/s'arrête.
 *  - Polling toutes les 12s : rattrape les changements de morceau/titre au sein d'une session déjà
 *    active, que le listener seul ne détecte pas forcément (MediaSession ne notifie pas toujours
 *    de changement de "liste active" pour un simple changement de piste).
 *
 * Foreground service (notification discrète) : sur les OEM agressifs sur la gestion batterie
 * (MIUI/Xiaomi en particulier), un service en pur arrière-plan peut voir son accès réseau coupé.
 * Le passer en foreground réduit fortement ce risque.
 */
class MediaWatcherService : NotificationListenerService() {

    private lateinit var tokenStore: TokenStore
    private lateinit var trackedPackagesCache: TrackedPackagesCache
    private lateinit var lastDetectionStore: LastDetectionStore
    private var lastPushedKey: String? = null

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        controllers?.let { checkSessions(it) }
    }

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(applicationContext)
        trackedPackagesCache = TrackedPackagesCache(applicationContext)
        lastDetectionStore = LastDetectionStore(applicationContext)
        startForegroundWithNotification()
        startPollingLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val componentName = ComponentName(this, MediaWatcherService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionListener, componentName)
        checkSessions(manager.getActiveSessions(componentName))
    }

    private fun startPollingLoop() {
        scope.launch {
            while (true) {
                delay(12_000)
                runCatching {
                    val manager = getSystemService(MediaSessionManager::class.java)
                    val componentName = ComponentName(this@MediaWatcherService, MediaWatcherService::class.java)
                    checkSessions(manager.getActiveSessions(componentName))
                }
            }
        }
    }

    private fun checkSessions(controllers: List<MediaController>) {
        scope.launch {
            val tracked = fetchTrackedPackages()

            val relevant = controllers.firstOrNull { it.packageName in tracked && isPlaying(it) }
            if (relevant == null) {
                lastPushedKey = null
                return@launch
            }

            val metadata = relevant.metadata ?: return@launch
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return@launch
            val subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            val key = "${relevant.packageName}:$title:$subtitle"
            if (key == lastPushedKey) return@launch
            lastPushedKey = key

            pushNowPlaying(relevant.packageName, title, subtitle, artBitmap)
        }
    }

    private suspend fun fetchTrackedPackages(): Set<String> {
        return runCatching {
            val api = ApiClient.create(tokenStore)
            val response = api.getMediaApps()
            if (response.isSuccessful) {
                val names = response.body()?.map { it.package_name }?.toSet() ?: emptySet()
                if (names.isNotEmpty()) trackedPackagesCache.set(names)
                names
            } else {
                trackedPackagesCache.get()
            }
        }.getOrElse { trackedPackagesCache.get() }
    }

    private fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    private suspend fun pushNowPlaying(packageName: String, title: String, subtitle: String?, imageBitmap: Bitmap?) {
        val imageBase64 = runCatching { imageBitmap?.let { bitmapToBase64Jpeg(it) } }
            .getOrElse {
                // L'encodage de l'image a échoué (bitmap trop gros/invalide) : on envoie quand même
                // le texte sans image plutôt que de tout annuler.
                null
            }

        runCatching {
            val api = ApiClient.create(tokenStore)
            val response = api.pushNowPlaying(packageName, title, subtitle, null, "playing", imageBase64)
            if (response.isSuccessful) {
                lastDetectionStore.setDetected(packageName, title, subtitle, pushOk = true)
            } else {
                lastDetectionStore.setDetected(
                    packageName, title, subtitle, pushOk = false,
                    errorDetail = "HTTP ${response.code()}",
                )
            }
        }.onFailure { e ->
            lastDetectionStore.setDetected(
                packageName, title, subtitle, pushOk = false,
                errorDetail = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    /** Redimensionne + compresse en JPEG pour rester léger avant l'envoi en base64. */
    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val maxSize = 256
        val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun startForegroundWithNotification() {
        val channelId = "media_watcher"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Détection média", NotificationManager.IMPORTANCE_MIN)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Détection média active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 4271
    }
}
