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
    // 注意：此处通知 ID 不能与 AcTimerService.NOTIFICATION_ID (1001) 重合，
    // 否则执行通知会覆盖前台服务常驻通知，导致系统认为 Service 不再前台而被回收。
    // 用一个完全独立的 ID 范围，且每次执行用不同 ID，便于多条历史并存。
    private const val EXEC_NOTIFICATION_BASE_ID = 2000

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
        taskId: String,
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
        nm.notify(notificationIdFor(taskId), notification)
    }

    fun showFailed(
        context: Context,
        taskId: String,
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
        nm.notify(notificationIdFor(taskId), notification)
    }

    /** 不同任务用不同通知 ID，避免同名任务互相覆盖；范围避开前台服务用的 1001。 */
    private fun notificationIdFor(taskId: String): Int {
        val hash = taskId.hashCode() and 0x7FFFFFFF
        return EXEC_NOTIFICATION_BASE_ID + (hash % 1000)
    }
}