package studio.rocknite.blog.media

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * L'accès aux notifications (donc aux MediaSessions) ne peut pas être demandé via une popup
 * de permission classique : il faut rediriger l'utilisateur vers les réglages système dédiés.
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?: return false
    return enabledListeners.contains(context.packageName)
}

fun openNotificationListenerSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
