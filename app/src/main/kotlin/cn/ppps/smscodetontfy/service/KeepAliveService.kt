package cn.ppps.smscodetontfy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cn.ppps.smscodetontfy.MainActivity
import cn.ppps.smscodetontfy.R
import cn.ppps.smscodetontfy.data.AppSettings
import cn.ppps.smscodetontfy.util.SmsCodeUtils

class KeepAliveService : Service() {
    companion object {
        const val ACTION_COPY_CLIPBOARD = "cn.ppps.smscodetontfy.action.COPY_CLIPBOARD"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "keepalive"

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                // ignore
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }

        /**
         * 把剪贴板写入交给前台服务执行：前台服务被系统视为「使用中」，
         * 在 MIUI/HyperOS 等限制后台写剪贴板的 ROM 上更可能被放行。
         * 保活未开启（服务不在前台）或无法启动服务时返回 false，由调用方直接写入兜底。
         */
        fun copyToClipboard(ctx: Context, text: String): Boolean {
            if (!AppSettings.foregroundKeepAlive) return false
            val i = Intent(ctx, KeepAliveService::class.java).apply {
                action = ACTION_COPY_CLIPBOARD
                putExtra(EXTRA_TEXT, text)
            }
            return try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        if (intent?.action == ACTION_COPY_CLIPBOARD) {
            val text = intent.getStringExtra(EXTRA_TEXT)
            if (!text.isNullOrEmpty()) {
                SmsCodeUtils.copySmsCodeToClipboard(this, text)
            }
            // 仅当保活关闭时，复制完立即停止，避免复制命令意外常驻
            if (!AppSettings.foregroundKeepAlive) stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "前台保活", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        val immutableFlag = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return builder
            .setContentTitle("SMS → ntfy 运行中")
            .setContentText("正在监听短信并转发到 ntfy")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
