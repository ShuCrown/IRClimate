package com.example.irpoc.model

import java.util.UUID

enum class RepeatType {
    ONCE, DAILY, WORKDAY
}

fun RepeatType.label(): String = when (this) {
    RepeatType.ONCE -> "仅一次"
    RepeatType.DAILY -> "每天"
    RepeatType.WORKDAY -> "工作日"
}

/** 空调运行模式（code 对应 AUX 协议字节） */
enum class AcMode(val code: Int, val label: String) {
    COOL(0x20, "制冷"),
    HEAT(0x40, "制热"),
    DRY(0x10, "除湿"),
    AUTO(0x00, "自动");

    companion object {
        fun fromCode(code: Int): AcMode = entries.find { it.code == code } ?: COOL
    }
}

/** 风速档位（code 对应 AUX 协议字节） */
enum class AcFan(val code: Int, val label: String) {
    AUTO(0xA0, "自动"),
    LOW(0x60, "低风"),
    MID(0x40, "中风"),
    HIGH(0x20, "高风");

    companion object {
        fun fromCode(code: Int): AcFan = entries.find { it.code == code } ?: AUTO
    }
}

data class AcTimerTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
    val targetTemp: Int,
    val mode: AcMode = AcMode.COOL,
    val fan: AcFan = AcFan.AUTO,
    val sleep: Boolean = false,
    val quiet: Boolean = false,
    val repeatType: RepeatType = RepeatType.WORKDAY,
    val enabled: Boolean = true,
)

fun AcTimerTask.timeText(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 任务下发状态的简短描述，用于列表/通知/事件 */
fun AcTimerTask.settingSummary(): String {
    val extras = buildList {
        if (sleep) add("睡眠")
        if (quiet) add("静音")
    }
    val tag = if (extras.isNotEmpty()) " · ${extras.joinToString("+")}" else ""
    return "${mode.label} ${targetTemp}°C · ${fan.label}$tag"
}
