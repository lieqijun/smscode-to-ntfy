package cn.ppps.smscodetontfy.data

import android.content.Context
import android.content.SharedPreferences

enum class Mode(val value: String) {
    NORMAL("normal"), LSPOSED("lsposed");

    companion object {
        fun from(value: String?): Mode = values().firstOrNull { it.value == value } ?: NORMAL
    }
}

object AppSettings {
    lateinit var appContext: Context
    private const val PREF = "smscode_ntfy_settings"

    private fun prefs(): SharedPreferences =
        appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    var autoCopySmsCode: Boolean
        get() = prefs().getBoolean("auto_copy_sms_code", true)
        set(v) = prefs().edit().putBoolean("auto_copy_sms_code", v).apply()

    var foregroundKeepAlive: Boolean
        get() = prefs().getBoolean("foreground_keep_alive", true)
        set(v) = prefs().edit().putBoolean("foreground_keep_alive", v).apply()

    var bootStart: Boolean
        get() = prefs().getBoolean("boot_start", true)
        set(v) = prefs().edit().putBoolean("boot_start", v).apply()

    var mode: Mode
        get() = Mode.from(prefs().getString("mode", null))
        set(v) = prefs().edit().putString("mode", v.value).apply()
}
