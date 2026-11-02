package cn.ppps.smscodetontfy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import cn.ppps.smscodetontfy.data.AppSettings
import cn.ppps.smscodetontfy.data.ConfigStore
import cn.ppps.smscodetontfy.data.Mode
import cn.ppps.smscodetontfy.service.KeepAliveService
import cn.ppps.smscodetontfy.util.NtfySender
import cn.ppps.smscodetontfy.util.SmsCodeUtils

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
        val format = if (Build.VERSION.SDK_INT >= 23) intent.getStringExtra("format") else null
        val sb = StringBuilder()
        for (pdu in pdus) {
            if (pdu !is ByteArray) continue
            val sms = if (Build.VERSION.SDK_INT >= 23) {
                SmsMessage.createFromPdu(pdu, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu)
            }
            sb.append(sms.messageBody)
        }
        val text = sb.toString()
        if (text.isEmpty()) return

        val appCtx = context.applicationContext
        val code = SmsCodeUtils.extractSmsCode(text)
        val clip = code ?: text

        // 1) 自动复制验证码
        if (AppSettings.autoCopySmsCode) {
            if (AppSettings.mode == Mode.LSPOSED) {
                // LSPosed 模式：Xposed 已绕过剪贴板后台写限制，直接写即可
                SmsCodeUtils.copySmsCodeToClipboard(appCtx, clip)
            } else {
                // 普通模式：优先前台服务写（后台受限 ROM 上更易放行），失败则直接写兜底
                if (!KeepAliveService.copyToClipboard(appCtx, clip)) {
                    SmsCodeUtils.copySmsCodeToClipboard(appCtx, clip)
                }
            }
        }

        // 2) 推送到 ntfy：只推验证码，提取不到则整条短信兜底
        val cfg = ConfigStore.load(appCtx)
        if (cfg != null && cfg.enabled) {
            Thread { NtfySender.send(cfg, clip) }.start()
        }
    }
}
