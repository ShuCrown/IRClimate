package com.example.irpoc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.irpoc.model.EventType
import com.example.irpoc.model.TimerEvent

@Composable
fun TimerEventItem(
    event: TimerEvent,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 事件图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(eventIconColor(event.type)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    eventIconSymbol(event.type),
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // 事件内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.summary(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkText,
                    maxLines = 1
                )
                if (event.detail.isNotBlank()) {
                    Text(
                        text = event.detail,
                        fontSize = 12.sp,
                        color = GrayText,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 时间戳
            Text(
                text = event.formattedTime(),
                fontSize = 11.sp,
                color = LightGrayText
            )
        }
    }
}

private fun eventIconColor(type: EventType): Color = when (type) {
    EventType.TASK_CREATED -> Color(0xFFE8F5E9)  // 绿色淡底
    EventType.TASK_UPDATED -> Color(0xFFFFF3E0)  // 橙色淡底
    EventType.TASK_PUBLISHED -> Color(0xFFE3F2FD) // 蓝色淡底
    EventType.TASK_EXECUTED -> Color(0xFFE8F5E9)  // 绿色淡底
    EventType.TASK_CANCELLED -> Color(0xFFFFEBEE) // 红色淡底
}

private fun eventIconSymbol(type: EventType): String = when (type) {
    EventType.TASK_CREATED -> "➕"
    EventType.TASK_UPDATED -> "✏️"
    EventType.TASK_PUBLISHED -> "📤"
    EventType.TASK_EXECUTED -> "✅"
    EventType.TASK_CANCELLED -> "❌"
}