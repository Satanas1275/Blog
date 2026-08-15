package studio.rocknite.blog.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import studio.rocknite.blog.data.ReminderStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = ReminderStore(context)
        if (store.enabled) {
            ReminderScheduler.schedule(context, store.hour, store.minute)
        }
    }
}
