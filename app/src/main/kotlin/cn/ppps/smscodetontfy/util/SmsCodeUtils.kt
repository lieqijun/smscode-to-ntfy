package cn.ppps.smscodetontfy.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.regex.Pattern

/**
 * 验证码提取与剪贴板写入。
 *
 * 规则（组合收紧，避免把营销/通知短信里的编号、链接参数误判为验证码）：
 *  1. 先剔除链接与域名样式片段（如 m.10010.cn/qAZRw?u=7747bd…），其中的数字一律不参与匹配；
 *  2. 只有命中验证码语义关键词（验证码/code/otp/verification/pin 等）才继续，
 *     英文关键词要求为独立单词，避免 shipping 之类误命中 pin；
 *  3. 只在关键词之后 MAX_DISTANCE 个字符的窗口内就近查找，不再做全文兜底；
 *  4. 数字串长度仅 4 位或 6 位，且前后既不能是数字也不能是字母。
 * 未命中关键词的短信（积分提醒、账单、营销等）一律返回 null → 不复制、不推送。
 */
object SmsCodeUtils {

    /** 链接/域名片段：http(s)://…、www.… 以及 xxx.yyy.zzz/path?query 之类 */
    private val LINK_PATTERN = Pattern.compile(
        "(?i)(?:https?://|www\\.)\\S+|[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+(?:/\\S*)?"
    )

    /** 验证码语义关键词；英文词要求前后不再紧邻字母 */
    private val KEYWORD_PATTERN = Pattern.compile(
        "验证码|校验码|动态码|动态密码|确认码|激活码|安全码" +
            "|(?<![A-Za-z])(?:code|otp|verification|pin|passcode)(?![A-Za-z])",
        Pattern.CASE_INSENSITIVE
    )

    // 前后非数字且非字母、长度仅为 4 位或 6 位的数字串
    // 注意：必须使用捕获组 (…)，group(1) 才能取到数字串本身；非捕获组 (?:…) 会导致 group(1) 抛 IndexOutOfBoundsException
    private val CODE_PATTERN = Pattern.compile(
        "(?<![0-9A-Za-z])([0-9]{4}(?![0-9A-Za-z])|[0-9]{6}(?![0-9A-Za-z]))"
    )

    /** 关键词后允许查找验证码的最大字符距离 */
    private const val MAX_DISTANCE = 64

    fun extractSmsCode(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val clean = LINK_PATTERN.matcher(text).replaceAll(" ")
        val keywords = KEYWORD_PATTERN.matcher(clean)
        var from = 0
        while (keywords.find(from)) {
            val start = keywords.end().coerceAtMost(clean.length)
            val end = (start + MAX_DISTANCE).coerceAtMost(clean.length)
            val m = CODE_PATTERN.matcher(clean.substring(start, end))
            if (m.find()) return m.group(1)
            // 该关键词附近没有验证码，继续检查下一个关键词
            from = start
        }
        return null
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
