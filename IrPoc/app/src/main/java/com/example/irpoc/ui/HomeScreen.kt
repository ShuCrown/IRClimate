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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.RepeatType
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
    onPowerClick: () -> Unit,
    onAddTimer: () -> Unit,
    onTaskToggle: (AcTimerTask, Boolean) -> Unit,
    onNavigate: (Screen) -> Unit = {},
) {
    Scaffold(
        topBar = { HomeTopBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTimer,
                shape = CircleShape,
                containerColor = Teal,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建定时")
            }
        },
        bottomBar = { BottomNavBar(Screen.Home, onNavigate) }
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
                        "全部",
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
                                imageVector = Icons.Outlined.Schedule,
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
                    TimerTaskItem(task = task, onToggle = { onTaskToggle(task, it) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun HomeTopBar() {
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
        IconButton(
            onClick = { },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "通知",
                tint = DarkText,
                modifier = Modifier.size(24.dp)
            )
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        "制冷 · ${task.targetTemp}°C · 1小时",
                        fontSize = 13.sp,
                        color = GrayText
                    )
                }
            }
            Box {
                Text(
                    task.repeatType.label(),
                    fontSize = 12.sp,
                    color = GrayText,
                    modifier = Modifier
                        .offset(y = (-20).dp)
                        .align(Alignment.TopStart)
                )
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

@Composable
private fun BottomNavBar(
    current: Screen,
    onNavigate: (Screen) -> Unit,
) {
    val items = listOf(
        Screen.Home to ("首页" to Icons.Outlined.Home),
        Screen.Scene to ("场景" to Icons.Outlined.ViewModule),
        Screen.Timer to ("定时" to Icons.Outlined.Schedule),
        Screen.Profile to ("我的" to Icons.Outlined.Person),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (screen, pair) ->
            val (label, icon) = pair
            val selected = screen == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .padding(vertical = 4.dp)
                    .clickable { onNavigate(screen) }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) Teal else GrayText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (selected) Teal else GrayText
                )
            }
        }
    }
}
