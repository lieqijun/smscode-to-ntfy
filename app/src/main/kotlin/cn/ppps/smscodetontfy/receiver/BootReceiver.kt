package cn.ppps.smscodetontfy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.ppps.smscodetontfy.data.AppSettings
import cn.ppps.smscodetontfy.service.KeepAliveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // 开机自启开关开启时，开机后启动前台服务（作为进程入口）
        if (AppSettings.bootStart) {
            KeepAliveService.start(context)
        }
    }
}
