package com.example.irpoc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.RepeatType

/**
 * 开机/应用更新后重新调度所有定时任务。
 *
 * AlarmManager 闹钟不会跨重启持久化，所以必须监听：
 * - [Intent.ACTION_BOOT_COMPLETED]：用户解锁后的常规开机广播
 * - [Intent.ACTION_LOCKED_BOOT_COMPLETED]：直接启动模式下的开机广播
 * - [Intent.ACTION_MY_PACKAGE_REPLACED]：应用更新后
 *
 * 由于 AlarmManager.setAlarmClock() 不要求解锁用户，
 * 在 LOCKED_BOOT_COMPLETED 时也能调度；但读取 SharedPreferences
 * 需要用户解锁后才能访问，所以这里统一延迟到 BOOT_COMPLETED 处理。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("BootReceiver", "收到广播: $action")

        // 锁屏下的直接启动模式：SharedPreferences 还不可访问，等解锁广播
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        // 重新调度所有已启用且未来有效（或可重复）的任务
        val storage = TimerStorage(context)
        val tasks = storage.loadTasks()
        if (tasks.isEmpty()) {
            Log.d("BootReceiver", "无任务可调度")
            return
        }

        val now = System.currentTimeMillis()
        val toSchedule = mutableListOf<AcTimerTask>()
        val updatedTasks = tasks.map { task ->
            if (!task.enabled) return@map task
            // 单次任务：若已过期则不再调度；否则保留 alarmTime
            if (task.repeatType == RepeatType.ONCE) {
                if (task.alarmTime > now) {
                    toSchedule += task
                }
                return@map task
            }
            // 重复任务：重新计算下次时间
            val next = nextAlarmTime(task.hour, task.minute, task.repeatType)
            val updated = task.copy(alarmTime = next)
            toSchedule += updated
            updated
        }

        if (toSchedule.isEmpty()) {
            Log.d("BootReceiver", "无未来任务可调度")
            return
        }

        storage.saveTasks(updatedTasks)
        toSchedule.forEach { task ->
            AlarmScheduler.schedule(context, task, task.alarmTime)
        }

        // 拉起前台服务，恢复常驻通知
        try {
            ContextCompat.startForegroundService(
                context,
                AcTimerService.scheduleIntent(context),
            )
        } catch (e: Exception) {
            Log.w("BootReceiver", "拉起前台服务失败: ${e.message}")
        }

        Log.d("BootReceiver", "已重调度 ${toSchedule.size} 个任务")
    }
}
