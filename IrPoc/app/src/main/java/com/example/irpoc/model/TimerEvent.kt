package com.example.irpoc.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class EventType {
    /** 定时任务新建 */
    TASK_CREATED,
    /** 定时任务编辑 */
    TASK_UPDATED,
    /** 后台定时发布（倒计时开始） */
    TASK_PUBLISHED,
    /** 定时任务执行完成 */
    TASK_EXECUTED,
    /** 定时任务取消 */
    TASK_CANCELLED,
}

data class TimerEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: EventType,
    val taskName: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
) {
    fun formattedTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    fun summary(): String = when (type) {
        EventType.TASK_CREATED -> "新建定时 \"$taskName\""
        EventType.TASK_UPDATED -> "编辑定时 \"$taskName\""
        EventType.TASK_PUBLISHED -> "后台发布 \"$taskName\""
        EventType.TASK_EXECUTED -> "\"$taskName\" 执行完毕"
        EventType.TASK_CANCELLED -> "\"$taskName\" 已取消"
    }
}