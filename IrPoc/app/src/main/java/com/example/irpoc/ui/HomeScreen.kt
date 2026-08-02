package com.example.irpoc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.TimerEvent
import com.example.irpoc.model.label
import com.example.irpoc.model.timeText

@Composable
fun HomeScreen(
    currentTemp: Int,
    targetTemp: Int,
    modeName: String,
    fanName: String,
    runHours: Int,
    isPowerOn: Boolean,
    timerTasks: List<AcTimerTask>,
    timerEvents: List<TimerEvent> = emptyList(),
    onPowerClick: () -> Unit,
    onAddTimer: () -> Unit,
    onTaskToggle: (AcTimerTask, Boolean) -> Unit,
    onEditTask: (AcTimerTask) -> Unit,
    onDeleteTask: (AcTimerTask) -> Unit,
) {
    var taskToDelete by remember { mutableStateOf<AcTimerTask?>(null) }
    var showMessageSheet by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var lastEventSize by remember { mutableIntStateOf(timerEvents.size) }
    LaunchedEffect(timerEvents.size) {
        if (timerEvents.size > lastEventSize) {
            unreadCount += timerEvents.size - lastEventSize
        }
        lastEventSize = timerEvents.size
    }

    // 删除确认对话框
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("删除定时", fontWeight = FontWeight.Bold) },
            text = {
                Text("确定要删除「${task.name}」(${task.timeText()}) 吗？\n此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTask(task)
                        taskToDelete = null
                    }
                ) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
    Scaffold(
        topBar = {
            HomeTopBar(
                unreadCount = unreadCount,
                onBellClick = {
                    unreadCount = 0
                    showMessageSheet = true
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTimer,
                shape = CircleShape,
                containerColor = Teal,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建定时")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgGray)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                AcStatusCard(
                    currentTemp = currentTemp,
                    targetTemp = targetTemp,
                    modeName = modeName,
                    fanName = fanName,
                    runHours = runHours,
                    isPowerOn = isPowerOn,
                    onPowerClick = onPowerClick
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "定时任务",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        "${timerTasks.size}个",
                        fontSize = 14.sp,
                        color = GrayText
                    )
                }
            }

            if (timerTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = null,
                                tint = GrayText,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("暂无更多任务", color = GrayText, fontSize = 14.sp)
                            Text("点击右下角加号新建定时", color = LightGrayText, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(timerTasks, key = { it.id }) { task ->
                    TimerTaskItem(
                        task = task,
                        onToggle = { onTaskToggle(task, it) },
                        onEdit = { onEditTask(task) },
                        onDelete = { taskToDelete = task }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // 消息弹窗
    if (showMessageSheet) {
        MessageBottomSheet(
            events = timerEvents,
            onDismiss = { showMessageSheet = false }
        )
    }
}

@Composable
private fun HomeTopBar(
    unreadCount: Int = 0,
    onBellClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgGray)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "我的空调",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        BadgedBox(
            modifier = Modifier.align(Alignment.CenterEnd),
            badge = {
                if (unreadCount > 0) {
                    Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                }
            }
        ) {
            IconButton(onClick = onBellClick) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "消息",
                    tint = DarkText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AcStatusCard(
    currentTemp: Int,
    targetTemp: Int,
    modeName: String,
    fanName: String,
    runHours: Int,
    isPowerOn: Boolean,
    onPowerClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Teal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "❄",
                            fontSize = 24.sp,
                            color = Teal
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "客厅空调",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            "$modeName · ${targetTemp}°C",
                            fontSize = 14.sp,
                            color = GrayText
                        )
                        Text(
                            "已运行 ${runHours} 小时",
                            fontSize = 12.sp,
                            color = LightGrayText
                        )
                    }
                }
                IconButton(
                    onClick = onPowerClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isPowerOn) Teal else LightGrayText),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⏻",
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TempInfoColumn(label = "设定", value = "${targetTemp}°C")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(DividerGray)
                )
                TempInfoColumn(label = "当前", value = "${currentTemp}°C")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(DividerGray)
                )
                TempInfoColumn(label = "风速", value = fanName)
            }
        }
    }
}

@Composable
private fun TempInfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = GrayText)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
    }
}

@Composable
private fun TimerTaskItem(
    task: AcTimerTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.timeText(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        task.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${task.targetTemp}°C · ${task.repeatType.label()}",
                        fontSize = 13.sp,
                        color = GrayText
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = LightGrayText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Switch(
                    checked = task.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Teal,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = LightGrayText
                    )
                )
            }
        }
    }
}