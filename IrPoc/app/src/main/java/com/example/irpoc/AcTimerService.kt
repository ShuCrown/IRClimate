package com.example.irpoc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.irpoc.model.AcFan
import com.example.irpoc.model.AcMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 前台服务：后台倒计时 → 锁屏发射 IR */
class AcTimerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var irManager: ConsumerIrManager? = null
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastTargetTemp = 0
    private var lastMode = 0
    private var lastFan = 0

    // ── Intent actions / extras ──────────────────────────────
    companion object {
        const val CHANNEL_ID = "ac_timer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START  = "com.example.irpoc.TIMER_START"
        const val ACTION_CANCEL = "com.example.irpoc.TIMER_CANCEL"
        const val ACTION_TICK   = "com.example.irpoc.TIMER_TICK"

        const val EXTRA_DELAY_MIN  = "delay_min"
        const val EXTRA_TARGET_TEMP = "target_temp"
        const val EXTRA_MODE       = "mode"
        const val EXTRA_FAN        = "fan"
        const val EXTRA_SLEEP      = "sleep"
        const val EXTRA_QUIET      = "quiet"
        const val EXTRA_REMAINING  = "remaining"
        const val EXTRA_STATUS     = "status"

        /** 启动定时任务 */
        fun startIntent(
            ctx: Context,
            delayMin: Int,
            targetTemp: Int,
            mode: Int,
            fan: Int,
            sleep: Boolean = false,
            quiet: Boolean = false,
        ): Intent = Intent(ctx, AcTimerService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_DELAY_MIN, delayMin)
            putExtra(EXTRA_TARGET_TEMP, targetTemp)
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_FAN, fan)
            putExtra(EXTRA_SLEEP, sleep)
            putExtra(EXTRA_QUIET, quiet)
        }

        /** 取消定时 */
        fun cancelIntent(ctx: Context): Intent =
            Intent(ctx, AcTimerService::class.java).apply { action = ACTION_CANCEL }
    }

    // ── Lifecycle ────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        irManager = getSystemService(CONSUMER_IR_SERVICE) as ConsumerIrManager
        createChannel()
        // 初始化 WakeLock（锁屏后保持 CPU 运行）
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AcTimerService:TimerWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val delayMin   = intent.getIntExtra(EXTRA_DELAY_MIN, 30)
                val targetTemp = intent.getIntExtra(EXTRA_TARGET_TEMP, 26)
                val mode       = intent.getIntExtra(EXTRA_MODE, 0x20)
                val fan        = intent.getIntExtra(EXTRA_FAN, 0xA0)
                val sleep      = intent.getBooleanExtra(EXTRA_SLEEP, false)
                val quiet      = intent.getBooleanExtra(EXTRA_QUIET, false)
                startTimer(delayMin, targetTemp, mode, fan, sleep, quiet)
            }
            ACTION_CANCEL -> stopTimer()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ── Timer logic ──────────────────────────────────────────
    private fun startTimer(delayMin: Int, targetTemp: Int, mode: Int, fan: Int, sleep: Boolean = false, quiet: Boolean = false) {
        timerJob?.cancel()
        lastTargetTemp = targetTemp
        lastMode = mode
        lastFan = fan
        val totalSec = delayMin * 60
        val modeLabel = AcMode.fromCode(mode).label
        val fanLabel = AcFan.fromCode(fan).label
        val extras = buildList {
            if (sleep) add("睡眠")
            if (quiet) add("静音")
        }
        val tag = if (extras.isNotEmpty()) " · ${extras.joinToString("+")}" else ""
        val summary = "$modeLabel ${targetTemp}°C · $fanLabel$tag"

        // 前台通知
        startForeground(NOTIFICATION_ID, buildNotification("定时任务", "${delayMin}分钟后切换 $summary", totalSec))

        // 获取 WakeLock，防止锁屏后 CPU 休眠
        wakeLock?.acquire(totalSec * 1000L + 10000L) // 超时 = 倒计时 + 10s 余量

        // 广播：已启动
        broadcastTick("started", totalSec)

        timerJob = scope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000)
                remaining--
                // 更新通知
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification("定时任务", "剩余 ${remaining / 60}分${remaining % 60}秒 → $summary", remaining))
                // 广播：倒计时
                broadcastTick("running", remaining)
            }

            // 时间到 → 发射 IR
            val ctx = this@AcTimerService
            try {
                val data = auxAcData(powerOn = true, tempCelsius = targetTemp, mode = mode, fanSpeed = fan, sleep = sleep, quiet = quiet)
                val pattern = bytesToNecPattern(data)
                irManager?.transmit(38000, pattern)
                Log.d("AcTimerService", "定时任务执行: 已切换 $summary")

                // 更新通知为完成状态
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification("定时任务 ✓", "已切换 $summary", 0))
            } catch (e: Exception) {
                Log.e("AcTimerService", "IR 发射失败: ${e.message}")
            }

            // 广播：完成（携带最终下发状态）
            broadcastTick("done", 0, lastTargetTemp, lastMode, lastFan)
            // 释放 WakeLock
            releaseWakeLock()
            // 延迟一会儿再关，让用户看到通知
            delay(2000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        releaseWakeLock()
        broadcastTick("cancelled", 0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastTick(
        status: String,
        remaining: Int,
        targetTemp: Int = 0,
        mode: Int = 0,
        fan: Int = 0,
    ) {
        sendBroadcast(Intent(ACTION_TICK).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_REMAINING, remaining)
            if (status == "done") {
                putExtra(EXTRA_TARGET_TEMP, targetTemp)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_FAN, fan)
            }
        })
    }

    // ── Notification ─────────────────────────────────────────
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) { }
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "空调定时调温",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "定时调温倒计时通知"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String, remainingSec: Int): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(remainingSec > 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // 有倒计时时加取消按钮
        if (remainingSec > 0) {
            val cancelIntent = cancelIntent(this)
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                android.app.PendingIntent.getService(
                    this, 0, cancelIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        return builder.build()
    }
}