package cn.ppps.smscodetontfy.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.regex.Pattern

/**
 * 验证码提取与剪贴板写入。
 * 策略：先按关键词（验证码/code/otp/verification/pin 等）就近匹配 4 位或 6 位数字；
 * 找不到则兜底匹配短信中独立的 4 位或 6 位数字串。
 * 仅匹配 4 位 / 6 位验证码，其它长度（如 5 位、7 位、8 位）不视为验证码。
 */
object SmsCodeUtils {

    private val KEYWORDS = listOf(
        "验证码", "校验码", "动态码", "动态密码", "确认码", "激活码", "安全码",
        "code", "otp", "verification", "pin", "passcode"
    )

    // 前后非数字，长度仅为 4 位或 6 位的数字串（不满足则不视为验证码）
    private val CODE_PATTERN = Pattern.compile("(?<![0-9])(?:[0-9]{4}(?![0-9])|[0-9]{6}(?![0-9]))")

    fun extractSmsCode(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val lower = text.lowercase()
        for (kw in KEYWORDS) {
            val idx = lower.indexOf(kw)
            if (idx >= 0) {
                val start = (idx + kw.length).coerceAtMost(text.length)
                val sub = text.substring(start)
                val m = CODE_PATTERN.matcher(sub)
                if (m.find()) return m.group(1)
            }
        }
        val m = CODE_PATTERN.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    fun copySmsCodeToClipboard(context: Context, code: String?): Boolean {
        if (code.isNullOrEmpty()) return false
        return try {
            // 使用 applicationContext，避免 BroadcastReceiver 上下文在部分机型上写剪贴板被限制
            val ctx = context.applicationContext ?: context
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("sms_code", code))
            true
        } catch (e: Exception) {
            false
        }
    }
}
