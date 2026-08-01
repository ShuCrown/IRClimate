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

data class AcTimerTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
    val targetTemp: Int,
    val repeatType: RepeatType = RepeatType.WORKDAY,
    val enabled: Boolean = true,
)

fun AcTimerTask.timeText(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
