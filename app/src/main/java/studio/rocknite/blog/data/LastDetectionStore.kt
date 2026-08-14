package studio.rocknite.blog.data

import android.content.Context

/**
 * Garde une trace locale de la dernière détection MediaSession (et du résultat de l'envoi),
 * pour que l'app puisse afficher "quoi de détecté en ce moment" au lieu d'être une boîte noire.
 */
class LastDetectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_detection", Context.MODE_PRIVATE)

    fun setDetected(packageName: String, title: String, subtitle: String?, pushOk: Boolean) {
        prefs.edit()
            .putString("package", packageName)
            .putString("title", title)
            .putString("subtitle", subtitle)
            .putBoolean("push_ok", pushOk)
            .putLong("at", System.currentTimeMillis())
            .apply()
    }

    fun clearDetected() {
        prefs.edit().clear().apply()
    }

    data class Snapshot(
        val packageName: String,
        val title: String,
        val subtitle: String?,
        val pushOk: Boolean,
        val atMillis: Long,
    )

    fun get(): Snapshot? {
        val pkg = prefs.getString("package", null) ?: return null
        val title = prefs.getString("title", null) ?: return null
        return Snapshot(
            packageName = pkg,
            title = title,
            subtitle = prefs.getString("subtitle", null),
            pushOk = prefs.getBoolean("push_ok", false),
            atMillis = prefs.getLong("at", 0L),
        )
    }
}
