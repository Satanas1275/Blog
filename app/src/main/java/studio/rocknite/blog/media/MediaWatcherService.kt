package studio.rocknite.blog.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.rocknite.blog.data.LastDetectionStore
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.data.TrackedPackagesCache

/**
 * Écoute les MediaSessions actives du système (fonctionne même en fullscreen / écran verrouillé,
 * car c'est un mécanisme système indépendant de l'UI de l'appli source).
 *
 * La liste des apps suivies (package_name) et les messages affichés sont entièrement configurables
 * depuis l'app (écran "Apps suivies") et stockés côté serveur — ce service ne fait que pousser les
 * données brutes (package détecté, titre, artiste/épisode, pochette si dispo) vers /api/now-playing
 * via MediaDetector (logique partagée avec le bouton "Rafraîchir maintenant" de l'app).
 *
 * Deux mécanismes combinés pour la détection, plus fiables que l'écoute seule :
 *  - OnActiveSessionsChangedListener : réaction rapide quand une session démarre/s'arrête.
 *  - Polling toutes les 12s : rattrape les changements de morceau/titre au sein d'une session déjà
 *    active, que le listener seul ne détecte pas forcément (MediaSession ne notifie pas toujours
 *    de changement de "liste active" pour un simple changement de piste).
 *
 * Deux garde-fous pour ne jamais rester bloqué sur un ancien statut :
 *  - Heartbeat toutes les 5min : renvoie le même statut même si rien n'a changé, pour repousser
 *    l'expiration côté serveur tant que la lecture continue.
 *  - Effacement explicite dès que plus rien ne joue (DELETE /api/now-playing), en plus du filet de
 *    sécurité côté serveur qui expire automatiquement un statut trop vieux (voir NOW_PLAYING_STALE_MINUTES).
 *
 * Foreground service (notification discrète) : sur les OEM agressifs sur la gestion batterie
 * (MIUI/Xiaomi en particulier), un service en pur arrière-plan peut voir son accès réseau coupé.
 * Le passer en foreground réduit fortement ce risque. Si le service plante quand même ou que le
 * système le tue, le bouton "Rafraîchir maintenant" (écran Poster) permet de forcer une détection
 * sans dépendre de lui.
 */
class MediaWatcherService : NotificationListenerService() {

    private lateinit var tokenStore: TokenStore
    private lateinit var trackedPackagesCache: TrackedPackagesCache
    private lateinit var lastDetectionStore: LastDetectionStore
    private var lastPushedKey: String? = null
    private var lastHeartbeatAtMillis: Long = 0L

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
            val tracked = MediaDetector.fetchTrackedPackages(tokenStore, trackedPackagesCache)

            val relevant = controllers.firstOrNull { it.packageName in tracked && MediaDetector.isPlaying(it) }
            if (relevant == null) {
                if (lastPushedKey != null) {
                    // On jouait quelque chose de suivi juste avant : on prévient explicitement le
                    // serveur de l'arrêt plutôt que d'attendre l'expiration automatique (plus lente).
                    MediaDetector.clearNowPlaying(tokenStore)
                }
                lastPushedKey = null
                return@launch
            }

            val metadata = relevant.metadata ?: return@launch
            val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return@launch
            val subtitle = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)

            val key = "${relevant.packageName}:$title:$subtitle"
            val now = System.currentTimeMillis()
            val heartbeatDue = now - lastHeartbeatAtMillis >= HEARTBEAT_INTERVAL_MILLIS

            // Rien de nouveau et pas encore l'heure du heartbeat : rien à envoyer.
            if (key == lastPushedKey && !heartbeatDue) return@launch

            val artBitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            lastPushedKey = key
            lastHeartbeatAtMillis = now
            MediaDetector.pushNowPlaying(tokenStore, lastDetectionStore, relevant.packageName, title, subtitle, artBitmap)
        }
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
        private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60 * 1000L // 5 min
    }
}
