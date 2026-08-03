package com.example.irpoc

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.irpoc.model.AcFan
import com.example.irpoc.model.AcMode
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.EventType
import com.example.irpoc.model.RepeatType
import com.example.irpoc.model.TimerEvent
import com.example.irpoc.model.settingSummary
import com.example.irpoc.model.timeText
import com.example.irpoc.ui.HomeScreen
import com.example.irpoc.ui.TimerBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("IrPoc", "通知权限被拒绝，定时任务仍可运行但无通知")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(this, requestPermissionLauncher)
                }
            }
        }
    }
}

@Composable
fun MainScreen(context: Context, permissionLauncher: ActivityResultLauncher<String>) {
    // AC 下发状态（从持久化加载）
    val storage = remember { TimerStorage(context) }
    val savedState = remember { storage.loadAcState() }
    var isPowerOn by remember { mutableStateOf(savedState?.powerOn ?: true) }
    var targetTemp by remember { mutableIntStateOf(savedState?.targetTemp ?: 24) }
    var currentMode by remember { mutableIntStateOf(savedState?.mode ?: 0x20) }
    var currentFan by remember { mutableIntStateOf(savedState?.fan ?: 0xA0) }

    // 定时任务列表（从持久化加载）
    val timerTasks = remember {
        mutableStateListOf<AcTimerTask>().apply { addAll(storage.loadTasks()) }
    }

    // 事件消息列表
    val timerEvents = remember { mutableStateListOf<TimerEvent>() }
    fun addEvent(type: EventType, taskName: String, detail: String = "") {
        timerEvents.add(0, TimerEvent(type = type, taskName = taskName, detail = detail))
        if (timerEvents.size > 100) {
            timerEvents.removeRange(50, timerEvents.size)
        }
    }

    // 编辑中的任务（null 表示新建模式）
    var editingTask by remember { mutableStateOf<AcTimerTask?>(null) }
    var showTimerSheet by remember { mutableStateOf(false) }

    // 每次列表变化时自动持久化
    fun persist() = storage.saveTasks(timerTasks.toList())

    val modeName = remember(currentMode) { AcMode.fromCode(currentMode).label }
    val fanName = remember(currentFan) { AcFan.fromCode(currentFan).label }

    val irManager = remember {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (_: Exception) {
            null
        }
    }

    fun now() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    fun log(msg: String) {
        Log.d("IrPoc", "[${now()}] $msg")
    }

    fun sendAc(powerOn: Boolean, temp: Int, mode: Int, fan: Int) {
        try {
            val mgr = irManager
            if (mgr == null) {
                log("⚠️ 无红外发射器，仅更新状态")
                // 即使无红外也更新状态
                isPowerOn = powerOn
                targetTemp = temp
                currentMode = mode
                currentFan = fan
                storage.saveAcState(AcState(powerOn, temp, mode, fan))
                return
            }
            val data = auxAcData(powerOn, temp, mode, fan)
            val pattern = bytesToNecPattern(data)
            mgr.transmit(38000, pattern)
            val mName = AcMode.fromCode(mode).label
            val fName = AcFan.fromCode(fan).label
            log("✅ AUX: ${if (powerOn) "开机" else "关机"} ${temp}°C $mName $fName")
            // 同步下发状态并持久化
            isPowerOn = powerOn
            targetTemp = temp
            currentMode = mode
            currentFan = fan
            storage.saveAcState(AcState(powerOn, temp, mode, fan))
        } catch (e: Exception) {
            log("❌ AUX 发送异常: ${e.message}")
        }
    }

    /** 计算任务的下次执行时间并返回 alarmTime */
    fun computeAlarmTime(task: AcTimerTask): Long {
        if (!task.enabled || task.repeatType == RepeatType.ONCE) {
            // 单次任务且已执行过（alarmTime 已过时），不再调度
            val now = System.currentTimeMillis()
            if (task.alarmTime > now) return task.alarmTime
            return 0
        }
        // 如果已有有效的 alarmTime 且未过期，保留
        val now = System.currentTimeMillis()
        if (task.alarmTime > now) return task.alarmTime
        return nextAlarmTime(task.hour, task.minute, task.repeatType)
    }

    /** 调度所有已启用任务：计算 alarmTime + 注册 AlarmManager + 启动前台服务 */
    fun scheduleTasks(tasks: List<AcTimerTask>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Android 12+ 需要检查精确闹钟权限
        val canExact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        for (task in tasks) {
            if (!task.enabled) continue
            val alarmTime = task.alarmTime
            if (alarmTime <= 0 || alarmTime <= System.currentTimeMillis()) continue
            val pi = TimerReceiver.pendingIntent(
                context, task.id, task.name,
                task.targetTemp, task.mode.code, task.fan.code,
                task.sleep, task.quiet
            )
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
            }
            log("⏰ 调度: ${task.name} @ ${task.timeText()}")
        }
        // 启动前台服务（常驻通知）
        ContextCompat.startForegroundService(context, AcTimerService.scheduleIntent(context))
    }

    /** 取消单个任务的闹钟 */
    fun cancelTaskAlarm(taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = android.app.PendingIntent.getBroadcast(
            context, taskId.hashCode(),
            Intent(context, TimerReceiver::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
        )
        pi?.let { am.cancel(it) }
    }

    /** 计算 alarmTime 并调度所有启用的任务 */
    fun refreshAllSchedules() {
        // 先更新所有任务的 alarmTime
        val updated = timerTasks.map { task ->
            if (task.enabled) {
                val alarmTime = computeAlarmTime(task)
                task.copy(alarmTime = alarmTime)
            } else {
                task
            }
        }
        timerTasks.clear()
        timerTasks.addAll(updated)
        persist()
        // 取消所有旧闹钟（通过服务统一处理）
        context.startService(AcTimerService.scheduleIntent(context))
    }

    // TimerReceiver 广播监听
    val timerReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val taskId = intent?.getStringExtra(TimerReceiver.EXTRA_TASK_ID) ?: return
                val temp = intent.getIntExtra(TimerReceiver.EXTRA_TARGET_TEMP, 26)
                val mode = intent.getIntExtra(TimerReceiver.EXTRA_MODE, 0x20)
                val fan = intent.getIntExtra(TimerReceiver.EXTRA_FAN, 0xA0)

                // 同步下发状态
                isPowerOn = true
                targetTemp = temp
                currentMode = mode
                currentFan = fan
                storage.saveAcState(AcState(true, temp, mode, fan))

                // 更新任务状态（计算下次 alarmTime）
                val idx = timerTasks.indexOfFirst { it.id == taskId }
                if (idx >= 0) {
                    val task = timerTasks[idx]
                    // 非 ONCE 任务重新计算下次执行时间，避免覆盖 rescheduleIfRepeat 已写入存储的正确值
                    val newAlarmTime = if (task.repeatType == RepeatType.ONCE) {
                        0L
                    } else {
                        nextAlarmTime(task.hour, task.minute, task.repeatType)
                    }
                    timerTasks[idx] = task.copy(alarmTime = newAlarmTime)
                    persist()
                    val detail = "${AcMode.fromCode(mode).label} ${targetTemp}°C · ${AcFan.fromCode(fan).label}"
                    addEvent(EventType.TASK_EXECUTED, task.name, "已切换 $detail")
                }
                log("✅ 定时执行: 已切换 $targetTemp°C")
            }
        }
    }

    LaunchedEffect(Unit) {
        val filter = IntentFilter(TimerReceiver.ACTION_TIMER_EXECUTED)
        context.registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)

        // 应用启动时重调度所有未过期的任务（应对手机重启）
        val tasks = storage.loadTasks()
        val hasPending = tasks.any { it.enabled && it.alarmTime > 0 && it.alarmTime > System.currentTimeMillis() }
        if (hasPending) {
            ContextCompat.startForegroundService(context, AcTimerService.scheduleIntent(context))
        }
    }
    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(timerReceiver) }
    }

    // ── 自动更新检测 ──────────────────────────────────────
    val updater = remember { AppUpdater(context) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        updateInfo = updater.checkUpdate()
    }

    updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = {
                updater.dismissVersion(update.versionName)
                updateInfo = null
            },
            title = { Text("发现新版本", fontWeight = FontWeight.Bold) },
            text = { Text(update.releaseNotes) },
            confirmButton = {
                TextButton(onClick = {
                    updater.downloadAndInstall(update)
                    updater.dismissVersion(update.versionName)
                    updateInfo = null
                }) {
                    Text("立即更新")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    updater.dismissVersion(update.versionName)
                    updateInfo = null
                }) {
                    Text("稍后再说")
                }
            }
        )
    }

    HomeScreen(
        targetTemp = targetTemp,
        modeName = modeName,
        fanName = fanName,
        isPowerOn = isPowerOn,
        timerTasks = timerTasks,
        timerEvents = timerEvents,
        onMarkAllRead = {
            for (i in timerEvents.indices) {
                if (!timerEvents[i].read) {
                    timerEvents[i] = timerEvents[i].copy(read = true)
                }
            }
        },
        onPowerClick = {
            isPowerOn = !isPowerOn
            sendAc(isPowerOn, targetTemp, currentMode, currentFan)
        },
        onAddTimer = { showTimerSheet = true },
        onEditTask = { task ->
            editingTask = task
            showTimerSheet = true
        },
        onTaskToggle = { task, enabled ->
            val idx = timerTasks.indexOfFirst { it.id == task.id }
            if (idx >= 0) {
                if (enabled) {
                    // 启用：计算 alarmTime → 调度
                    val alarmTime = nextAlarmTime(task.hour, task.minute, task.repeatType)
                    val updated = task.copy(enabled = true, alarmTime = alarmTime)
                    timerTasks[idx] = updated
                    persist()
                    scheduleTasks(listOf(updated))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    addEvent(EventType.TASK_PUBLISHED, task.name, "已调度")
                } else {
                    // 禁用：取消闹钟
                    cancelTaskAlarm(task.id)
                    val updated = task.copy(enabled = false, alarmTime = 0)
                    timerTasks[idx] = updated
                    persist()
                    addEvent(EventType.TASK_CANCELLED, task.name, "用户关闭")
                    context.startService(AcTimerService.cancelIntent(context, task.id))
                }
            }
        },
        onDeleteTask = { task ->
            cancelTaskAlarm(task.id)
            timerTasks.removeAll { it.id == task.id }
            persist()
            addEvent(EventType.TASK_CANCELLED, task.name, "已删除")
            context.startService(AcTimerService.cancelIntent(context, task.id))
            log("🗑 删除定时: ${task.name}")
        }
    )

    // 定时任务编辑/新增弹窗
    if (showTimerSheet) {
        TimerBottomSheet(
            initialTask = editingTask,
            defaultTemp = targetTemp,
            defaultMode = AcMode.fromCode(currentMode),
            defaultFan = AcFan.fromCode(currentFan),
            onDismiss = {
                showTimerSheet = false
                editingTask = null
            },
            onSave = { task ->
                if (editingTask != null) {
                    // 编辑模式：替换已有任务
                    val idx = timerTasks.indexOfFirst { it.id == editingTask!!.id }
                    if (idx >= 0) {
                        timerTasks[idx] = task
                    }
                    addEvent(EventType.TASK_UPDATED, task.name, "${task.timeText()} → ${task.settingSummary()}")
                } else {
                    // 新建模式
                    timerTasks.add(task)
                    addEvent(EventType.TASK_CREATED, task.name, "${task.timeText()} → ${task.settingSummary()}")
                }
                persist()
                editingTask = null
                showTimerSheet = false
                if (task.enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    // 计算 alarmTime 并调度
                    val alarmTime = nextAlarmTime(task.hour, task.minute, task.repeatType)
                    val idx = timerTasks.indexOfFirst { it.id == task.id }
                    if (idx >= 0) {
                        timerTasks[idx] = task.copy(alarmTime = alarmTime)
                        persist()
                    }
                    scheduleTasks(listOf(task.copy(alarmTime = alarmTime)))
                    addEvent(EventType.TASK_PUBLISHED, task.name, "已调度")
                }
            }
        )
    }
}

/**
 * 生成 NEC 协议 IR pattern（微秒数组）。
 */
fun necPattern(data: Int): IntArray {
    val list = mutableListOf(9000, 4500)
    for (i in 0 until 32) {
        val bit = (data shr i) and 1
        list.add(562)
        list.add(if (bit == 1) 1688 else 562)
    }
    list.add(562)
    return list.toIntArray()
}

/**
 * 奥克斯空调 13 字节协议编码。
 */
fun auxAcData(
    powerOn: Boolean,
    tempCelsius: Int,
    mode: Int,
    fanSpeed: Int,
    swingH: Boolean = true,
    sleep: Boolean = false,
    eco: Boolean = false,
    quiet: Boolean = false,
): ByteArray {
    val tempVal = (tempCelsius - 8).coerceIn(0, 31)
    val byte2 = ((tempVal shl 3) or 0b101).toByte()
    val byte3 = (if (swingH) 0xE0 else 0x00).toByte()
    // byte7: 模式 + 睡眠
    val byte7 = (mode or (if (sleep) 0x04 else 0x00)).toByte()
    // byte8: 静音（原未使用的字节）
    val byte8 = (if (quiet) 0x08 else 0x00).toByte()
    val byte10 = ((if (powerOn) 0x20 else 0x00) or (if (eco) 0x08 else 0x00)).toByte()
    val byte12 = 0x45.toByte()

    val header = byteArrayOf(
        0xC3.toByte(), byte2, byte3, 0x00, fanSpeed.toByte(), 0x00,
        byte7, byte8, 0x00, byte10, 0x00, byte12
    )
    val checksum = (header.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
    return header + checksum
}

/**
 * 将字节数组转换为 NEC 格式 IR pattern。
 */
fun bytesToNecPattern(data: ByteArray): IntArray {
    val list = mutableListOf(9000, 4500)
    for (byte in data) {
        for (i in 0 until 8) {
            val bit = (byte.toInt() shr i) and 1
            list.add(562)
            list.add(if (bit == 1) 1688 else 562)
        }
    }
    list.add(562)
    return list.toIntArray()
}