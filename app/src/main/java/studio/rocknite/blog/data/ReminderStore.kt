package studio.rocknite.blog.data

import android.content.Context

class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var hour: Int
        get() = prefs.getInt("hour", 20)
        set(value) = prefs.edit().putInt("hour", value).apply()

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(value) = prefs.edit().putInt("minute", value).apply()
}
