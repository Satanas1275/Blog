package studio.rocknite.blog.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import studio.rocknite.blog.MainActivity
import studio.rocknite.blog.data.ReminderStore

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(context)

        // Reprogramme pour le lendemain, tant que le rappel est toujours activé.
        val store = ReminderStore(context)
        if (store.enabled) {
            ReminderScheduler.schedule(context, store.hour, store.minute)
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "daily_reminder"
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Rappel quotidien", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, channelId)
            .setContentTitle("Quoi de neuf ?")
            .setContentText("Un petit post pour la journée ?")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 4273
    }
}
