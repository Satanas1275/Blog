package studio.rocknite.blog.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Base64
import studio.rocknite.blog.data.LastDetectionStore
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.data.TrackedPackagesCache
import studio.rocknite.blog.network.ApiClient
import studio.rocknite.blog.network.pushNowPlaying
import java.io.ByteArrayOutputStream

/**
 * Logique de détection MediaSession + push vers /api/now-playing, factorisée pour être appelable
 * depuis deux endroits : le service en arrière-plan (MediaWatcherService, automatique) et le bouton
 * "Rafraîchir maintenant" de l'app (manuel — utile justement quand le service plante ou s'arrête).
 *
 * Contrairement au service, checkOnce() ne dépend pas d'une instance de service vivante : elle a
 * juste besoin de l'accès aux notifications accordé pour l'app (MediaSessionManager.getActiveSessions
 * marche tant que la permission est là, que le service tourne ou non).
 */
object MediaDetector {

    /** @return true si quelque chose de suivi a été détecté et poussé (ou heartbeat renvoyé), false sinon. */
    suspend fun checkOnce(
        context: Context,
        tokenStore: TokenStore,
        trackedPackagesCache: TrackedPackagesCache,
        lastDetectionStore: LastDetectionStore,
        force: Boolean = false,
    ): Boolean {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val componentName = ComponentName(context, MediaWatcherService::class.java)
        val controllers = runCatching { manager.getActiveSessions(componentName) }.getOrElse { emptyList() }

        val tracked = fetchTrackedPackages(tokenStore, trackedPackagesCache)
        val relevant = controllers.firstOrNull { it.packageName in tracked && isPlaying(it) }

        if (relevant == null) {
            if (force) lastDetectionStore.clearDetected()
            return false
        }

        val metadata = relevant.metadata ?: return false
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return false
        val subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        pushNowPlaying(tokenStore, lastDetectionStore, relevant.packageName, title, subtitle, artBitmap)
        return true
    }

    suspend fun fetchTrackedPackages(tokenStore: TokenStore, trackedPackagesCache: TrackedPackagesCache): Set<String> {
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

    fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    suspend fun pushNowPlaying(
        tokenStore: TokenStore,
        lastDetectionStore: LastDetectionStore,
        packageName: String,
        title: String,
        subtitle: String?,
        imageBitmap: Bitmap?,
    ) {
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

    suspend fun clearNowPlaying(tokenStore: TokenStore) {
        runCatching {
            val api = ApiClient.create(tokenStore)
            api.deleteNowPlaying()
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
