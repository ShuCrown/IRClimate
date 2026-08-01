package com.example.irpoc

import android.Manifest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.ui.CreateTimerScreen
import com.example.irpoc.ui.HomeScreen
import com.example.irpoc.ui.Screen
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
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // AC 状态
    var currentTemp by remember { mutableIntStateOf(26) }
    var targetTemp by remember { mutableIntStateOf(24) }
    var currentMode by remember { mutableIntStateOf(0x20) }
    var currentFan by remember { mutableIntStateOf(0xA0) }
    var isPowerOn by remember { mutableStateOf(true) }
    var runHours by remember { mutableIntStateOf(2) }

    // 定时任务列表（内存态）
    val timerTasks = remember {
        mutableStateListOf(
            AcTimerTask(name = "起床降温", hour = 7, minute = 30, targetTemp = 23, repeatType = com.example.irpoc.model.RepeatType.WORKDAY),
            AcTimerTask(name = "午休节能", hour = 13, minute = 0, targetTemp = 26, repeatType = com.example.irpoc.model.RepeatType.WORKDAY),
            AcTimerTask(name = "睡前除湿", hour = 22, minute = 30, targetTemp = 24, repeatType = com.example.irpoc.model.RepeatType.DAILY, enabled = false),
        )
    }

    // Service 广播监听
    var timerActive by remember { mutableStateOf(false) }
    var timerRemainingSec by remember { mutableIntStateOf(0) }

    val modeMap = remember {
        listOf(0x20 to "制冷", 0x40 to "制热", 0x10 to "除湿", 0x00 to "自动")
    }
    val fanMap = remember {
        listOf(0xA0 to "自动", 0x60 to "低风", 0x40 to "中风", 0x20 to "高风")
    }

    val modeName = modeMap.find { it.first == currentMode }?.second ?: "制冷"
    val fanName = fanMap.find { it.first == currentFan }?.second ?: "自动"

    val irManager = remember {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
    }

    fun now() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    fun log(msg: String) {
        Log.d("IrPoc", "[${now()}] $msg")
    }

    fun sendAc(powerOn: Boolean, temp: Int, mode: Int, fan: Int) {
        try {
            val data = auxAcData(powerOn, temp, mode, fan)
            val pattern = bytesToNecPattern(data)
            irManager.transmit(38000, pattern)
            val mName = modeMap.find { it.first == mode }?.second ?: "?"
            log("✅ AUX: ${if (powerOn) "开机" else "关机"} ${temp}°C $mName")
        } catch (e: Exception) {
            log("❌ AUX 发送异常: ${e.message}")
        }
    }

    fun startTimerTask(task: AcTimerTask) {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, task.hour)
            set(java.util.Calendar.MINUTE, task.minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        val delayMin = ((target.timeInMillis - now.timeInMillis) / 60000).toInt()
        val intent = AcTimerService.startIntent(context, delayMin, task.targetTemp, currentMode, currentFan)
        ContextCompat.startForegroundService(context, intent)
        log("⏰ 启动定时: ${task.name} ${task.hour.toString().padStart(2,'0')}:${task.minute.toString().padStart(2,'0')} → ${task.targetTemp}°C (${delayMin}分钟后)")
    }

    val timerReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getStringExtra(AcTimerService.EXTRA_STATUS) ?: return
                when (status) {
                    "started" -> {
                        timerActive = true
                        timerRemainingSec = intent.getIntExtra(AcTimerService.EXTRA_REMAINING, 0)
                    }
                    "running" -> timerRemainingSec = intent.getIntExtra(AcTimerService.EXTRA_REMAINING, 0)
                    "done", "cancelled" -> {
                        timerActive = false
                        timerRemainingSec = 0
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val filter = IntentFilter(AcTimerService.ACTION_TICK)
        context.registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)
    }
    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(timerReceiver) }
    }

    when (currentScreen) {
        Screen.Home -> HomeScreen(
            currentTemp = currentTemp,
            targetTemp = targetTemp,
            modeName = modeName,
            fanName = fanName,
            runHours = runHours,
            isPowerOn = isPowerOn,
            timerTasks = timerTasks,
            onPowerClick = {
                isPowerOn = !isPowerOn
                sendAc(isPowerOn, targetTemp, currentMode, currentFan)
            },
            onAddTimer = { currentScreen = Screen.Timer },
            onTaskToggle = { task, enabled ->
                val idx = timerTasks.indexOfFirst { it.id == task.id }
                if (idx >= 0) {
                    val updated = task.copy(enabled = enabled)
                    timerTasks[idx] = updated
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        startTimerTask(updated)
                    } else {
                        context.stopService(AcTimerService.cancelIntent(context))
                    }
                }
            },
            onNavigate = { currentScreen = it }
        )
        Screen.Timer -> CreateTimerScreen(
            onBack = { currentScreen = Screen.Home },
            onSave = { task ->
                timerTasks.add(task)
                currentScreen = Screen.Home
                if (task.enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    startTimerTask(task)
                }
            }
        )
        else -> HomeScreen(
            currentTemp = currentTemp,
            targetTemp = targetTemp,
            modeName = modeName,
            fanName = fanName,
            runHours = runHours,
            isPowerOn = isPowerOn,
            timerTasks = timerTasks,
            onPowerClick = {
                isPowerOn = !isPowerOn
                sendAc(isPowerOn, targetTemp, currentMode, currentFan)
            },
            onAddTimer = { currentScreen = Screen.Timer },
            onTaskToggle = { task, enabled ->
                val idx = timerTasks.indexOfFirst { it.id == task.id }
                if (idx >= 0) timerTasks[idx] = task.copy(enabled = enabled)
            },
            onNavigate = { currentScreen = it }
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
): ByteArray {
    val tempVal = (tempCelsius - 8).coerceIn(0, 31)
    val byte2 = ((tempVal shl 3) or 0b101).toByte()
    val byte3 = (if (swingH) 0xE0 else 0x00).toByte()
    val byte7 = (mode or (if (sleep) 0x04 else 0x00)).toByte()
    val byte10 = ((if (powerOn) 0x20 else 0x00) or (if (eco) 0x08 else 0x00)).toByte()
    val byte12 = 0x45.toByte()

    val header = byteArrayOf(
        0xC3.toByte(), byte2, byte3, 0x00, fanSpeed.toByte(), 0x00,
        byte7, 0x00, 0x00, byte10, 0x00, byte12
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
