package studio.rocknite.blog.data

import android.content.Context

/**
 * Cache local des package_name suivis pour MediaWatcherService. Le service resynchronise
 * depuis /api/media-apps régulièrement, mais garde ce cache pour continuer à fonctionner
 * si le serveur est temporairement injoignable.
 */
class TrackedPackagesCache(context: Context) {
    private val prefs = context.getSharedPreferences("tracked_packages", Context.MODE_PRIVATE)

    fun get(): Set<String> = prefs.getString(KEY, null)?.split(SEPARATOR)?.toSet() ?: emptySet()

    fun set(packages: Set<String>) {
        prefs.edit().putString(KEY, packages.joinToString(SEPARATOR)).apply()
    }

    companion object {
        private const val KEY = "packages"
        private const val SEPARATOR = "|||"
    }
}
