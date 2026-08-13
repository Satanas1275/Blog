package studio.rocknite.blog.media

import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.network.ApiClient

/**
 * Écoute les MediaSessions actives du système (fonctionne même en fullscreen / écran verrouillé,
 * car c'est un mécanisme système indépendant de l'UI de l'appli source).
 *
 * v0 : détecte titre/artiste via les metadata MediaSession pour YouTube Music et Crunchyroll,
 * et pousse l'état vers /api/now-playing.
 *
 * TODO (passe suivante) :
 *  - Patch ReVanced YouTube/YT Music pour broadcaster l'ID vidéo exact + l'état de navigation
 *    (home/vidéo/short), plus fiable que les metadata MediaSession brutes.
 *  - Fallback lien Crunchyroll par recherche du titre (pas d'API/patch dispo, appli fermée).
 */
class MediaWatcherService : NotificationListenerService() {

    private lateinit var tokenStore: TokenStore
    private var lastPushedKey: String? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        controllers?.let { onActiveSessionsChanged(it) }
    }

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val componentName = android.content.ComponentName(this, MediaWatcherService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionListener, componentName)
        onActiveSessionsChanged(manager.getActiveSessions(componentName))
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>) {
        val relevant = controllers.firstOrNull { isTrackedApp(it.packageName) && isPlaying(it) }
            ?: return

        val metadata = relevant.metadata ?: return
        val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)

        val key = "${relevant.packageName}:$title:$artist"
        if (key == lastPushedKey) return
        lastPushedKey = key

        pushNowPlaying(
            source = sourceFor(relevant.packageName),
            title = title,
            subtitle = artist,
            link = null, // v0 : pas de lien fiable sans patch ReVanced, voir TODO
        )
    }

    private fun isTrackedApp(packageName: String) = packageName in TRACKED_PACKAGES

    private fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun sourceFor(packageName: String): String = when (packageName) {
        "com.google.android.apps.youtube.music" -> "youtube_music"
        "com.google.android.youtube" -> "youtube"
        "com.crunchyroll.crunchyroid" -> "crunchyroll"
        else -> "unknown"
    }

    private fun pushNowPlaying(source: String, title: String, subtitle: String?, link: String?) {
        // Appel réseau simple en fire-and-forget ; à remplacer par un vrai scope de coroutine
        // avec retry/backoff dans une passe suivante.
        Thread {
            runCatching {
                val api = ApiClient.create(tokenStore)
                kotlinx.coroutines.runBlocking {
                    api.pushNowPlaying(source, title, subtitle, link, "playing")
                }
            }
        }.start()
    }

    companion object {
        private val TRACKED_PACKAGES = setOf(
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube",
            "com.crunchyroll.crunchyroid",
        )
    }
}
