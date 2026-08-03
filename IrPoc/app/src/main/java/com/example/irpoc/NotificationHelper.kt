package com.example.irpoc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.irpoc.model.AcFan
import com.example.irpoc.model.AcMode

/**
 * 通知工具：定时任务执行通知。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "ac_timer_channel"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "空调定时任务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "定时任务执行通知"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun showExecuted(
        context: Context,
        taskName: String,
        targetTemp: Int,
        mode: Int,
        fan: Int,
        sleep: Boolean = false,
        quiet: Boolean = false,
    ) {
        ensureChannel(context)
        val modeLabel = AcMode.fromCode(mode).label
        val fanLabel = AcFan.fromCode(fan).label
        val extras = buildList {
            if (sleep) add("睡眠")
            if (quiet) add("静音")
        }
        val tag = if (extras.isNotEmpty()) " · ${extras.joinToString("+")}" else ""
        val summary = "$modeLabel ${targetTemp}°C · $fanLabel$tag"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("定时任务 ✓")
            .setContentText("$taskName: 已切换 $summary")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun showFailed(
        context: Context,
        taskName: String,
        targetTemp: Int,
        mode: Int,
        fan: Int,
        sleep: Boolean = false,
        quiet: Boolean = false,
        reason: String? = null,
    ) {
        ensureChannel(context)
        val modeLabel = AcMode.fromCode(mode).label
        val fanLabel = AcFan.fromCode(fan).label
        val extras = buildList {
            if (sleep) add("睡眠")
            if (quiet) add("静音")
        }
        val tag = if (extras.isNotEmpty()) " · ${extras.joinToString("+")}" else ""
        val summary = "$modeLabel ${targetTemp}°C · $fanLabel$tag"
        val text = "$taskName: 红外下发失败 ($summary)" + (reason?.let { " - $it" } ?: "")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("定时任务 ✗")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }
}