package studio.rocknite.blog.data

import android.content.Context

/**
 * Liste éditable des statuts rapides ("En voiture", "Dodo"...), stockée en local sur le tel.
 * Rien de sensible ici, pas besoin de chiffrement (contrairement à TokenStore).
 */
class QuickStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("quick_status", Context.MODE_PRIVATE)

    fun getLabels(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULTS
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun addLabel(label: String) {
        val current = getLabels()
        if (label in current) return
        prefs.edit().putString(KEY, (current + label).joinToString(SEPARATOR)).apply()
    }

    fun removeLabel(label: String) {
        val current = getLabels()
        prefs.edit().putString(KEY, (current - label).joinToString(SEPARATOR)).apply()
    }

    companion object {
        private const val KEY = "labels"
        private const val SEPARATOR = "|||"
        private val DEFAULTS = listOf("En voiture", "Dodo", "Au taf", "Sur un projet")
    }
}
