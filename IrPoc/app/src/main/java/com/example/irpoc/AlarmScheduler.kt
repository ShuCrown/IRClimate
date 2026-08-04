package com.example.irpoc

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.timeText

/**
 * 统一的闹钟调度入口。
 *
 * 关键设计：
 * 1. 优先使用 [AlarmManager.setAlarmClock] —— 它不受 Doze 配额限制，
 *    可在锁屏/Doze 下可靠唤醒，是用户期望的"闹钟"语义。
 * 2. 退回到 [AlarmManager.setExactAndAllowWhileIdle]（Android 12+ 需精确闹钟权限）。
 * 3. 最低优先级用 [AlarmManager.setAndAllowWhileIdle] 兜底。
 *
 * 注意：[PendingIntent] 必须使用 FLAG_UPDATE_CURRENT，避免 FLAG_CANCEL_CURRENT
 * 在重建 PendingIntent 时连带取消已注册的闹钟。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    /** 是否拥有精确闹钟权限（Android 12+）。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * 注册一个任务的闹钟。会自动选择最优 API。
     *
     * @param triggerAtMillis 触发的墙钟时间戳（毫秒）
     */
    fun schedule(
        context: Context,
        task: AcTimerTask,
        triggerAtMillis: Long,
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "跳过过期闹钟: ${task.name} @ ${task.timeText()}")
            return
        }
        val pi = TimerReceiver.pendingIntent(
            context = context,
            taskId = task.id,
            taskName = task.name,
            targetTemp = task.targetTemp,
            mode = task.mode.code,
            fan = task.fan.code,
            sleep = task.sleep,
            quiet = task.quiet,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, null)
        // setAlarmClock 在 Doze 下也能立即触发，是最可靠的"闹钟"语义 API
        am.setAlarmClock(info, pi)
        Log.d(TAG, "📅 调度: ${task.name} @ ${task.timeText()} (setAlarmClock)")
    }

    /**
     * 取消一个任务的闹钟。会静默忽略不存在的 PendingIntent。
     */
    fun cancel(context: Context, taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            android.content.Intent(context, TimerReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
            Log.d(TAG, "🗑 取消闹钟: $taskId")
        }
    }
}
