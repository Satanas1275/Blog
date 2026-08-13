package studio.rocknite.blog.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Base64
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.network.ApiClient
import java.io.ByteArrayOutputStream

/**
 * Écoute les MediaSessions actives du système (fonctionne même en fullscreen / écran verrouillé,
 * car c'est un mécanisme système indépendant de l'UI de l'appli source).
 *
 * v0 (pas de patch ReVanced pour l'instant) — traitement différent par appli :
 *  - YouTube Music : titre + artiste + pochette si dispo (l'appli les expose via MediaMetadata)
 *  - Crunchyroll   : titre (nom de l'anime/épisode) + image si dispo
 *  - YouTube       : message générique "Regarde des vidéos sur YouTube", pas de titre par vidéo
 *    (sans patch, pas moyen fiable de distinguer scroll du feed vs lecture d'une vidéo précise)
 *
 * TODO (passe suivante) : patch ReVanced YouTube/YT Music pour un lien direct + état de navigation précis.
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
        val componentName = ComponentName(this, MediaWatcherService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionListener, componentName)
        onActiveSessionsChanged(manager.getActiveSessions(componentName))
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>) {
        val relevant = controllers.firstOrNull { isTrackedApp(it.packageName) && isPlaying(it) }
        if (relevant == null) {
            lastPushedKey = null
            return
        }

        when (relevant.packageName) {
            "com.google.android.apps.youtube.music" -> handleTitledSource(relevant, "youtube_music")
            "com.crunchyroll.crunchyroid" -> handleTitledSource(relevant, "crunchyroll")
            "com.google.android.youtube" -> handleGenericYoutube()
        }
    }

    /** YT Music et Crunchyroll : on pousse le vrai titre (+ artiste/épisode) + l'image si dispo. */
    private fun handleTitledSource(controller: MediaController, source: String) {
        val metadata = controller.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val key = "$source:$title:$subtitle"
        if (key == lastPushedKey) return
        lastPushedKey = key

        pushNowPlaying(source = source, title = title, subtitle = subtitle, imageBitmap = artBitmap)
    }

    /** YouTube : pas de titre précis sans patch, juste un message générique tant qu'une vidéo joue. */
    private fun handleGenericYoutube() {
        val key = "youtube:generic"
        if (key == lastPushedKey) return
        lastPushedKey = key

        pushNowPlaying(source = "youtube", title = "Regarde des vidéos sur YouTube", subtitle = null, imageBitmap = null)
    }

    private fun isTrackedApp(packageName: String) = packageName in TRACKED_PACKAGES

    private fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun pushNowPlaying(source: String, title: String, subtitle: String?, imageBitmap: Bitmap?) {
        val imageBase64 = imageBitmap?.let { bitmapToBase64Jpeg(it) }

        // Fire-and-forget simple ; à remplacer par un vrai scope coroutine avec retry/backoff
        // si ça se révèle instable en usage réel.
        Thread {
            runCatching {
                val api = ApiClient.create(tokenStore)
                kotlinx.coroutines.runBlocking {
                    api.pushNowPlaying(source, title, subtitle, null, "playing", imageBase64)
                }
            }
        }.start()
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

    companion object {
        private val TRACKED_PACKAGES = setOf(
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube",
            "com.crunchyroll.crunchyroid",
        )
    }
}

