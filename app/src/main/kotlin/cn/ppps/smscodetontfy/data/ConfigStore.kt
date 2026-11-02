package cn.ppps.smscodetontfy.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object ConfigStore {
    private const val PREF = "smscode_ntfy_config"
    private const val KEY = "config"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun load(ctx: Context): NtfyConfig? {
        val json = prefs(ctx).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(json)
            NtfyConfig(
                server = o.optString("server", ""),
                topic = o.optString("topic", ""),
                token = o.optString("token", ""),
                clientCert = o.optString("clientCert", ""),
                enabled = o.optBoolean("enabled", true)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(ctx: Context, config: NtfyConfig) {
        val o = JSONObject()
        o.put("server", config.server)
        o.put("topic", config.topic)
        o.put("token", config.token)
        o.put("clientCert", config.clientCert)
        o.put("enabled", config.enabled)
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }
}
