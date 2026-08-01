package com.example.irpoc

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IrPocScreen(this)
                }
            }
        }
    }
}

@Composable
fun IrPocScreen(context: Context) {
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
        Text("IR PoC — vivo 标准接口验证", style = MaterialTheme.typography.headlineSmall)
        Divider()

        Button(onClick = {
            try {
                val pattern = necPattern(0x00FF00FF)
                irManager.transmit(38000, pattern)
                log("transmit 成功: 38000Hz, NEC 0x00FF00FF, ${pattern.size}段")
                log("👉 用另一手机摄像头对准本机顶部IR灯，应见紫白光点")
            } catch (e: Exception) {
                log("transmit 抛异常: ${e.javaClass.name}")
                log("  message: ${e.message}")
                e.stackTraceToString().lineSequence().take(8).forEach { log("  $it") }
            }
        }) {
            Text("发射 NEC 测试码 (38kHz)")
        }

        Button(onClick = {
            try {
                val pattern = necPattern(0x00FF00FF)
                repeat(3) {
                    irManager.transmit(38000, pattern)
                    Thread.sleep(40)
                }
                log("连发3次完成")
            } catch (e: Exception) {
                log("连发异常: ${e.javaClass.name}: ${e.message}")
            }
        }) {
            Text("连发 3 次（增强可见性）")
        }

        Button(onClick = {
            try {
                irManager.transmit(38000, intArrayOf(9000, 4500, 562, 1688, 562, 562, 562))
                log("短码发射成功（未抛异常）")
            } catch (e: Exception) {
                log("短码异常: ${e.javaClass.name}: ${e.message}")
            }
        }) {
            Text("发射短测试码")
        }

        Divider()
        Text("奥克斯空调", style = MaterialTheme.typography.titleMedium)

        Button(onClick = {
            try {
                val acData = auxAcData(powerOn = true, tempCelsius = 26, mode = 0x20, fanSpeed = 0xA0)
                val pattern = bytesToNecPattern(acData)
                irManager.transmit(38000, pattern)
                log("AUX 开机: 26°C 制冷 自动风, ${pattern.size}段")
                log("👉 对准空调红外接收窗")
            } catch (e: Exception) {
                log("AUX 开机异常: ${e.javaClass.name}: ${e.message}")
            }
        }) {
            Text("AUX 开机 (26°C 制冷)")
        }

        Button(onClick = {
            try {
                val acData = auxAcData(powerOn = false, tempCelsius = 26, mode = 0x20, fanSpeed = 0xA0)
                val pattern = bytesToNecPattern(acData)
                irManager.transmit(38000, pattern)
                log("AUX 关机: 发送关机码, ${pattern.size}段")
            } catch (e: Exception) {
                log("AUX 关机异常: ${e.javaClass.name}: ${e.message}")
            }
        }) {
            Text("AUX 关机")
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
