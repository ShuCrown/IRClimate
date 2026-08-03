package com.example.irpoc

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.settingSummary
import com.example.irpoc.model.timeText

/**
 * 前台服务：管理定时任务闹钟 + 持续通知。
 *
 * 职责：
 * 1. 用 AlarmManager.setExactAndAllowWhileIdle() 调度所有已启用任务
 * 2. 显示通知栏常驻通知，提示最近一次任务时间
 * 3. 取消时移除闹钟并关闭通知
 */
class AcTimerService : Service() {

    private val storage by lazy { TimerStorage(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tasks = storage.loadTasks()
        when (intent?.action) {
            ACTION_SCHEDULE -> {
                scheduleAll(tasks)
                showNotification(tasks)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                cancelAlarm(taskId)
                val remaining = tasks.filter { it.enabled && it.alarmTime > 0 }
                if (remaining.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    showNotification(remaining)
                }
            }
            ACTION_REFRESH -> {
                if (tasks.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    showNotification(tasks)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 调度 ────────────────────────────────────────────────
    private fun scheduleAll(tasks: List<AcTimerTask>) {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        for (task in tasks) {
            if (!task.enabled || task.alarmTime <= 0) continue
            val pi = TimerReceiver.pendingIntent(
                this, task.id, task.name,
                task.targetTemp, task.mode.code, task.fan.code,
                task.sleep, task.quiet
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.alarmTime, pi)
            Log.d("AcTimerService", "📅 调度任务: ${task.name} @ ${task.timeText()}")
        }
    }

    private fun cancelAlarm(taskId: String?) {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        if (taskId != null) {
            val pi = PendingIntent.getBroadcast(
                this, taskId.hashCode(),
                Intent(this, TimerReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            pi?.let { am.cancel(it) }
            Log.d("AcTimerService", "🗑 取消闹钟: $taskId")
        }
    }

    // ── 通知 ────────────────────────────────────────────────
    private fun ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "空调定时任务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "后台定时任务"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun showNotification(tasks: List<AcTimerTask>) {
        ensureChannel()
        val activeTasks = tasks.filter { it.enabled && it.alarmTime > 0 && it.alarmTime > System.currentTimeMillis() }
        val nextTask = activeTasks.minByOrNull { it.alarmTime }
        val title = if (activeTasks.size <= 1) "定时任务" else "${activeTasks.size} 个定时任务"
        val text = if (nextTask != null) {
            "${nextTask.timeText()} → ${nextTask.settingSummary()}"
        } else {
            "无待执行任务"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "ac_timer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SCHEDULE = "com.example.irpoc.TIMER_SCHEDULE"
        const val ACTION_CANCEL   = "com.example.irpoc.TIMER_CANCEL"
        const val ACTION_REFRESH  = "com.example.irpoc.TIMER_REFRESH"
        const val EXTRA_TASK_ID   = "task_id"

        fun scheduleIntent(ctx: Context): Intent =
            Intent(ctx, AcTimerService::class.java).apply { action = ACTION_SCHEDULE }

        fun cancelIntent(ctx: Context, taskId: String? = null): Intent =
            Intent(ctx, AcTimerService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }

        fun refreshIntent(ctx: Context): Intent =
            Intent(ctx, AcTimerService::class.java).apply { action = ACTION_REFRESH }
    }
}