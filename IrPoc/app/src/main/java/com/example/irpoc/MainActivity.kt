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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
                    IrPocScreen(this, requestPermissionLauncher)
                }
            }
        }
    }
}

@Composable
fun IrPocScreen(context: Context, permissionLauncher: ActivityResultLauncher<String>) {
    val logs = remember { mutableStateListOf<String>() }
    val scrollState = rememberScrollState()

    fun now() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    fun log(msg: String) {
        val line = "[${now()}] $msg"
        logs.add(0, line)
        Log.d("IrPoc", msg)
    }

    val irManager = remember {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
    }

    // 启动自动检测
    LaunchedEffect(Unit) {
        log("ConsumerIrManager 实例: $irManager")
        val has = irManager.hasIrEmitter()
        log("hasIrEmitter() = $has")
        val freqs = irManager.carrierFrequencies
        if (freqs == null || freqs.isEmpty()) {
            log("carrierFrequencies = 空")
        } else {
            freqs.forEach {
                log("频段: ${it.minFrequency}Hz ~ ${it.maxFrequency}Hz")
            }
        }
        if (has) {
            log("✅ 标准接口识别到 IR 硬件，点下方按钮实发验证")
        } else {
            log("⚠️ hasIrEmitter=false：标准接口不可用")
            log("   vivo 多见此情况——IR 走私有 API")
            log("   路线改判：需研究 vivo 智能遥控私有 Intent/反射")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("奥克斯空调遥控", style = MaterialTheme.typography.headlineSmall)
        Divider()

        // ===== 状态变量 =====
        var currentTemp by remember { mutableIntStateOf(26) }
        var currentMode by remember { mutableIntStateOf(0x20) }
        var currentFan by remember { mutableIntStateOf(0xA0) }
        var timerTargetTemp by remember { mutableIntStateOf(28) }
        var timerDelayMin by remember { mutableIntStateOf(30) }
        var timerActive by remember { mutableStateOf(false) }
        var timerRemainingSec by remember { mutableIntStateOf(0) }
        var isCustomDelay by remember { mutableStateOf(false) }
        var customDelayInput by remember { mutableStateOf("") }
        var isScheduleMode by remember { mutableStateOf(false) }
        var scheduleHour by remember { mutableIntStateOf(22) }
        var scheduleMinute by remember { mutableIntStateOf(0) }

        // 监听 Service 广播
        val timerReceiver = remember {
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val status = intent?.getStringExtra(AcTimerService.EXTRA_STATUS) ?: return
                    when (status) {
                        "started" -> {
                            timerActive = true
                            timerRemainingSec = intent.getIntExtra(AcTimerService.EXTRA_REMAINING, 0)
                            log("⏰ 后台定时调温已启动")
                        }
                        "running" -> {
                            timerRemainingSec = intent.getIntExtra(AcTimerService.EXTRA_REMAINING, 0)
                        }
                        "done" -> {
                            timerActive = false
                            timerRemainingSec = 0
                            log("✅ 后台定时调温执行完毕")
                        }
                        "cancelled" -> {
                            timerActive = false
                            timerRemainingSec = 0
                            log("⏹ 后台定时调温已取消")
                        }
                    }
                }
            }
        }

        // 注册/注销广播接收器
        LaunchedEffect(Unit) {
            val filter = IntentFilter(AcTimerService.ACTION_TICK)
            context.registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)
        }
        // 利用 DisposableEffect 在 composable 离开时注销
        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose { context.unregisterReceiver(timerReceiver) }
        }

        val modeMap = listOf(0x20 to "制冷", 0x40 to "制热", 0x10 to "除湿", 0x00 to "自动")
        val fanMap = listOf(0xA0 to "自动", 0x60 to "低风", 0x40 to "中风", 0x20 to "高风")

        fun sendAc(powerOn: Boolean, temp: Int, mode: Int, fan: Int) {
            try {
                val data = auxAcData(powerOn, temp, mode, fan)
                val pattern = bytesToNecPattern(data)
                irManager.transmit(38000, pattern)
                val modeName = modeMap.find { it.first == mode }?.second ?: "?"
                log("✅ AUX: ${if (powerOn) "开机" else "关机"} ${temp}°C $modeName")
            } catch (e: Exception) {
                log("❌ AUX 发送异常: ${e.message}")
            }
        }

        // ===== 温度调节 =====
        Text("温度", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(onClick = { if (currentTemp > 16) currentTemp-- }) { Text("-") }
            Spacer(Modifier.width(16.dp))
            Text("${currentTemp}°C", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Button(onClick = { if (currentTemp < 31) currentTemp++ }) { Text("+") }
        }

        // ===== 模式选择 =====
        Text("模式", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modeMap.forEach { (code, label) ->
                if (code == currentMode) {
                    Button(onClick = { currentMode = code }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { currentMode = code }) { Text(label) }
                }
            }
        }

        // ===== 风速选择 =====
        Text("风速", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fanMap.forEach { (code, label) ->
                if (code == currentFan) {
                    Button(onClick = { currentFan = code }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { currentFan = code }) { Text(label) }
                }
            }
        }

        // ===== 立即调温 =====
        Button(onClick = { sendAc(powerOn = true, currentTemp, currentMode, currentFan) }) {
            Text("立即调温 → ${currentTemp}°C")
        }

        Spacer(Modifier.height(4.dp))
        Divider()
        Text("定时调温", style = MaterialTheme.typography.titleMedium)

        // ===== 目标温度 =====
        Text("目标温度", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(onClick = { if (timerTargetTemp > 16) timerTargetTemp-- }) { Text("-") }
            Spacer(Modifier.width(16.dp))
            Text("${timerTargetTemp}°C", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Button(onClick = { if (timerTargetTemp < 31) timerTargetTemp++ }) { Text("+") }
        }

        // ===== 模式切换 =====
        Text("触发方式", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isScheduleMode) {
                Button(onClick = { }) { Text("延时") }
            } else {
                OutlinedButton(onClick = { isScheduleMode = false }) { Text("延时") }
            }
            if (isScheduleMode) {
                Button(onClick = { }) { Text("指定时间") }
            } else {
                OutlinedButton(onClick = { isScheduleMode = true; isCustomDelay = false }) { Text("指定时间") }
            }
        }

        if (isScheduleMode) {
            // ===== 指定时间 =====
            Text("时间", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = scheduleHour.toString().padStart(2, '0'),
                    onValueChange = { s ->
                        val v = s.filter { it.isDigit() }.take(2).toIntOrNull()
                        if (v != null && v in 0..23) scheduleHour = v
                        else if (s.isEmpty()) scheduleHour = 0
                    },
                    label = { Text("时") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(8.dp))
                Text(":", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = scheduleMinute.toString().padStart(2, '0'),
                    onValueChange = { s ->
                        val v = s.filter { it.isDigit() }.take(2).toIntOrNull()
                        if (v != null && v in 0..59) scheduleMinute = v
                        else if (s.isEmpty()) scheduleMinute = 0
                    },
                    label = { Text("分") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(8.dp))
            }
        } else {
            // ===== 延时选择 =====
            Text("延时", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 120).forEach { min ->
                    if (!isCustomDelay && min == timerDelayMin) {
                        Button(onClick = { timerDelayMin = min; isCustomDelay = false }) { Text("${min}分钟") }
                    } else {
                        OutlinedButton(onClick = { timerDelayMin = min; isCustomDelay = false }) { Text("${min}分钟") }
                    }
                }
                if (isCustomDelay) {
                    Button(onClick = { }) { Text("自定义") }
                } else {
                    OutlinedButton(onClick = { isCustomDelay = true; customDelayInput = "" }) { Text("自定义") }
                }
            }
            if (isCustomDelay) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customDelayInput,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            customDelayInput = filtered
                            val mins = filtered.toIntOrNull()
                            if (mins != null && mins in 1..1440) {
                                timerDelayMin = mins
                            }
                        },
                        label = { Text("分钟") },
                        placeholder = { Text("1~1440") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("分钟 (1~1440)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ===== 定时启动/取消 =====
        if (timerActive) {
            Button(
                onClick = {
                    context.stopService(AcTimerService.cancelIntent(context))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("取消定时 (剩余 ${timerRemainingSec / 60}分${timerRemainingSec % 60}秒)")
            }
        } else {
            Button(onClick = {
                // Android 13+ 请求通知权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val delayMin = if (isScheduleMode) {
                    val now = java.util.Calendar.getInstance()
                    val target = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, scheduleHour)
                        set(java.util.Calendar.MINUTE, scheduleMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    if (target.timeInMillis <= now.timeInMillis) {
                        target.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                    ((target.timeInMillis - now.timeInMillis) / 60000).toInt()
                } else {
                    timerDelayMin
                }
                val intent = AcTimerService.startIntent(
                    context, delayMin, timerTargetTemp, currentMode, currentFan
                )
                ContextCompat.startForegroundService(context, intent)
                if (isScheduleMode) {
                    log("⏰ 启动定时调温: ${scheduleHour.toString().padStart(2,'0')}:${scheduleMinute.toString().padStart(2,'0')} → ${timerTargetTemp}°C (${delayMin}分钟后)")
                } else {
                    log("⏰ 启动后台定时: $delayMin 分钟后 → ${timerTargetTemp}°C")
                }
            }) {
                val label = if (isScheduleMode) {
                    "${scheduleHour.toString().padStart(2,'0')}:${scheduleMinute.toString().padStart(2,'0')} → ${timerTargetTemp}°C"
                } else {
                    "${timerTargetTemp}°C (${timerDelayMin}分钟后)"
                }
                Text("启动定时 $label")
            }
        }

        // ===== 快捷开关 =====
        Spacer(Modifier.height(4.dp))
        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                sendAc(powerOn = true, currentTemp, currentMode, currentFan)
            }) {
                Text("开机 ${currentTemp}°C")
            }
            Button(onClick = {
                sendAc(powerOn = false, currentTemp, currentMode, currentFan)
            }) {
                Text("关机")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Divider()
        Text("日志（最新在上）：", style = MaterialTheme.typography.titleMedium)
        logs.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 生成 NEC 协议 IR pattern（微秒数组）。
 * NEC: 9000 mark + 4500 space 引导，32 bit 数据（LSB 先），
 * 每 bit = 562 mark + (562=0 / 1688=1)，结尾 562 mark。载波 38kHz。
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
 * 基于 NEC 时序（38kHz, 562/1688），每字节 LSB 先发。
 * 第 13 字节 = 前 12 字节累加和 & 0xFF。
 *
 * @param powerOn true=开机, false=关机
 * @param tempCelsius 温度 16~31°C
 * @param mode 模式: 0x20=制冷, 0x40=制热, 0x10=除湿, 0x30=送风, 0x00=自动
 * @param fanSpeed 风速: 0xA0=自动, 0x60=低, 0x40=中, 0x20=高
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
    // byte2: 温度(高5位) | 上下吹风(低3位, 101=下吹风)
    val byte2 = ((tempVal shl 3) or 0b101).toByte()
    // byte3: 左右摆风 (0xE0=开)
    val byte3 = (if (swingH) 0xE0 else 0x00).toByte()
    // byte7: 模式(高4位) | 睡眠(bit2)
    val byte7 = (mode or (if (sleep) 0x04 else 0x00)).toByte()
    // byte10: 开关(bit5) | ECO(bit3)
    val byte10 = ((if (powerOn) 0x20 else 0x00) or (if (eco) 0x08 else 0x00)).toByte()
    // byte12: 按键码，0x45=开关
    val byte12 = 0x45.toByte()

    val header = byteArrayOf(
        0xC3.toByte(), byte2, byte3, 0x00, fanSpeed.toByte(), 0x00,
        byte7, 0x00, 0x00, byte10, 0x00, byte12
    )
    val checksum = (header.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
    return header + checksum
}

/**
 * 将字节数组转换为 NEC 格式 IR pattern（微秒脉冲数组）。
 * 引导码(9000+4500) + 每字节 LSB 先 8bit + 结束码(562)。
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
