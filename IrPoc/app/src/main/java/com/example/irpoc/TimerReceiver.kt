package com.example.irpoc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.util.Log
import com.example.irpoc.model.RepeatType
import com.example.irpoc.model.timeText

/**
 * 接收 AlarmManager 闹钟广播，执行 IR 发射并通知 UI。
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val targetTemp = intent.getIntExtra(EXTRA_TARGET_TEMP, 26)
        val mode = intent.getIntExtra(EXTRA_MODE, 0x20)
        val fan = intent.getIntExtra(EXTRA_FAN, 0xA0)
        val sleep = intent.getBooleanExtra(EXTRA_SLEEP, false)
        val quiet = intent.getBooleanExtra(EXTRA_QUIET, false)
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "定时任务"

        // 发射 IR，记录真实结果，避免"提示成功但空调没动"
        var irOk = false
        var failReason: String? = null
        try {
            val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            if (irManager != null) {
                val data = auxAcData(powerOn = true, tempCelsius = targetTemp, mode = mode, fanSpeed = fan, sleep = sleep, quiet = quiet)
                val pattern = bytesToNecPattern(data)
                irManager.transmit(38000, pattern)
                irOk = true
                Log.d("TimerReceiver", "✅ 定时执行: $taskName → ${targetTemp}°C")
            } else {
                failReason = "无红外发射器"
                Log.w("TimerReceiver", "⚠️ 无红外发射器，跳过 IR 发射")
            }
        } catch (e: Exception) {
            failReason = e.message
            Log.e("TimerReceiver", "❌ IR 发射失败: ${e.message}")
        }

        // 广播到 UI
        val broadcast = Intent(ACTION_TIMER_EXECUTED).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TARGET_TEMP, targetTemp)
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_FAN, fan)
            putExtra(EXTRA_IR_OK, irOk)
        }
        context.sendBroadcast(broadcast)

        // 显示通知（按真实结果显示成功/失败）
        if (irOk) {
            NotificationHelper.showExecuted(context, taskId, taskName, targetTemp, mode, fan, sleep, quiet)
        } else {
            NotificationHelper.showFailed(context, taskId, taskName, targetTemp, mode, fan, sleep, quiet, failReason)
        }

        // 如果是重复任务，调度下一次
        rescheduleIfRepeat(context, taskId, taskName, targetTemp, mode, fan, sleep, quiet)
    }

    private fun rescheduleIfRepeat(
        context: Context,
        taskId: String,
        taskName: String,
        targetTemp: Int,
        mode: Int,
        fan: Int,
        sleep: Boolean,
        quiet: Boolean,
    ) {
        val storage = TimerStorage(context)
        val tasks = storage.loadTasks()
        val task = tasks.find { it.id == taskId } ?: return
        if (task.repeatType == RepeatType.ONCE) return

        // 计算下次执行时间
        val nextAlarm = nextAlarmTime(task.hour, task.minute, task.repeatType)
        val updated = task.copy(alarmTime = nextAlarm)
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            val mutable = tasks.toMutableList()
            mutable[idx] = updated
            storage.saveTasks(mutable)
        }

        // 用统一调度入口（setAlarmClock，不受 Doze 配额限制）
        AlarmScheduler.schedule(context, updated, nextAlarm)
        Log.d("TimerReceiver", "📅 重复任务下次调度: ${task.timeText()}")
    }

    companion object {
        const val ACTION_TIMER_EXECUTED = "com.example.irpoc.TIMER_EXECUTED"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TARGET_TEMP = "target_temp"
        const val EXTRA_MODE = "mode"
        const val EXTRA_FAN = "fan"
        const val EXTRA_SLEEP = "sleep"
        const val EXTRA_QUIET = "quiet"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_IR_OK = "ir_ok"

        fun pendingIntent(
            context: Context,
            taskId: String,
            taskName: String,
            targetTemp: Int,
            mode: Int,
            fan: Int,
            sleep: Boolean = false,
            quiet: Boolean = false,
        ): android.app.PendingIntent {
            val intent = Intent(context, TimerReceiver::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_TARGET_TEMP, targetTemp)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_FAN, fan)
                putExtra(EXTRA_SLEEP, sleep)
                putExtra(EXTRA_QUIET, quiet)
            }
            // 用 FLAG_UPDATE_CURRENT 保留已注册的闹钟关联，仅更新 extras。
            // 此前用 FLAG_CANCEL_CURRENT 会连带取消 AlarmManager 已注册的闹钟，
            // 导致 scheduleAll() 调用时序错乱后闹钟丢失。
            return android.app.PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}

/**
 * 计算下次执行时间（毫秒时间戳）。
 */
fun nextAlarmTime(hour: Int, minute: Int, repeatType: RepeatType): Long {
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    // 如果今天已过，推到明天
    if (target.timeInMillis <= now.timeInMillis) {
        target.add(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    // 工作日：跳过周末
    if (repeatType == RepeatType.WORKDAY) {
        while (true) {
            val dow = target.get(java.util.Calendar.DAY_OF_WEEK)
            if (dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY) break
            target.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
    }
    return target.timeInMillis
}