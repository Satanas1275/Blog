package studio.rocknite.blog.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stocke le token d'API (bearer) et l'URL du serveur de façon chiffrée sur le device.
 * Jamais commité, jamais loggé.
 */
class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiToken: String?
        get() = prefs.getString("api_token", null)
        set(value) = prefs.edit().putString("api_token", value).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "https://blog.rocknite.studio") ?: "https://blog.rocknite.studio"
        set(value) = prefs.edit().putString("server_url", value).apply()
}
