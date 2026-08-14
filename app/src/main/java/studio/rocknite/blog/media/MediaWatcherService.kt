package studio.rocknite.blog.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Base64
import kotlinx.coroutines.runBlocking
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
 */
class MediaWatcherService : NotificationListenerService() {

    private lateinit var tokenStore: TokenStore
    private lateinit var trackedPackagesCache: TrackedPackagesCache
    private lateinit var lastDetectionStore: LastDetectionStore
    private var lastPushedKey: String? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        controllers?.let { onActiveSessionsChanged(it) }
    }

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(applicationContext)
        trackedPackagesCache = TrackedPackagesCache(applicationContext)
        lastDetectionStore = LastDetectionStore(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val componentName = ComponentName(this, MediaWatcherService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionListener, componentName)
        onActiveSessionsChanged(manager.getActiveSessions(componentName))
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>) {
        Thread {
            // Resynchronise la liste des packages suivis à chaque changement (peu fréquent,
            // appel léger) ; retombe sur le cache local si le serveur est injoignable.
            val tracked = fetchTrackedPackages()

            val relevant = controllers.firstOrNull { it.packageName in tracked && isPlaying(it) }
            if (relevant == null) {
                lastPushedKey = null
                return@Thread
            }

            val metadata = relevant.metadata ?: return@Thread
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return@Thread
            val subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            val key = "${relevant.packageName}:$title:$subtitle"
            if (key == lastPushedKey) return@Thread
            lastPushedKey = key

            pushNowPlaying(relevant.packageName, title, subtitle, artBitmap)
        }.start()
    }

    private fun fetchTrackedPackages(): Set<String> {
        return runCatching {
            val api = ApiClient.create(tokenStore)
            val apps = runBlocking { api.getMediaApps() }
            if (apps.isSuccessful) {
                val names = apps.body()?.map { it.package_name }?.toSet() ?: emptySet()
                if (names.isNotEmpty()) trackedPackagesCache.set(names)
                names
            } else {
                trackedPackagesCache.get()
            }
        }.getOrElse { trackedPackagesCache.get() }
    }

    private fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun pushNowPlaying(packageName: String, title: String, subtitle: String?, imageBitmap: Bitmap?) {
        val imageBase64 = imageBitmap?.let { bitmapToBase64Jpeg(it) }
        runCatching {
            val api = ApiClient.create(tokenStore)
            val response = runBlocking { api.pushNowPlaying(packageName, title, subtitle, null, "playing", imageBase64) }
            lastDetectionStore.setDetected(packageName, title, subtitle, response.isSuccessful)
        }.onFailure {
            lastDetectionStore.setDetected(packageName, title, subtitle, pushOk = false)
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
}
