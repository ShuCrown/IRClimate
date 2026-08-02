package com.example.irpoc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.irpoc.model.AcTimerTask
import com.example.irpoc.model.RepeatType
import com.example.irpoc.model.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerBottomSheet(
    initialTask: AcTimerTask? = null,
    defaultTemp: Int = 24,
    onDismiss: () -> Unit,
    onSave: (AcTimerTask) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialTask?.name ?: "") }
    var hour by remember { mutableIntStateOf(initialTask?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(initialTask?.minute ?: 30) }
    var targetTemp by remember { mutableIntStateOf(initialTask?.targetTemp ?: defaultTemp) }
    var repeatType by remember { mutableStateOf(initialTask?.repeatType ?: RepeatType.WORKDAY) }

    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgGray,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(
                if (initialTask != null) "编辑定时" else "新建定时",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 名称
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("名称", fontSize = 14.sp, color = GrayText)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("定时调温") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = Teal.copy(alpha = 0.3f),
                        )
                    )
                }
            }

            // 执行时间
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("执行时间", fontSize = 14.sp, color = GrayText)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TimeDigitField(
                            value = hour,
                            onValueChange = { hour = it.coerceIn(0, 23) },
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            ":",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        TimeDigitField(
                            value = minute,
                            onValueChange = { minute = it.coerceIn(0, 59) },
                            modifier = Modifier.width(72.dp)
                        )
                    }
                }
            }

            // 温度
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "温度",
                        fontSize = 14.sp,
                        color = GrayText,
                        modifier = Modifier.align(Alignment.Start).padding(start = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        TempButton(
                            text = "−",
                            onClick = { if (targetTemp > 16) targetTemp-- }
                        )
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Teal.copy(alpha = 0.3f), CircleShape)
                                .background(TealLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "$targetTemp",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                                Text(
                                    "°",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        TempButton(
                            text = "+",
                            onClick = { if (targetTemp < 30) targetTemp++ }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("16°C - 30°C", fontSize = 13.sp, color = GrayText)
                }
            }

            // 重复
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("重复", fontSize = 14.sp, color = GrayText)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RepeatType.entries.forEach { type ->
                            RepeatChip(
                                label = type.label(),
                                selected = repeatType == type,
                                onClick = { repeatType = type }
                            )
                        }
                    }
                }
            }

            // 保存按钮
            Button(
                onClick = {
                    onSave(
                        AcTimerTask(
                            name = name.ifBlank { "定时调温" },
                            hour = hour,
                            minute = minute,
                            targetTemp = targetTemp,
                            repeatType = repeatType
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) {
                Text(
                    if (initialTask != null) "保存修改" else "保存定时",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TimeDigitField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString().padStart(2, '0'),
        onValueChange = { s ->
            val filtered = s.filter { it.isDigit() }.take(2)
            val v = filtered.toIntOrNull()
            if (v != null) {
                onValueChange(v)
            } else if (s.isEmpty()) {
                onValueChange(0)
            }
        },
        modifier = modifier,
        textStyle = TextStyle(
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TealLight,
            unfocusedContainerColor = BgGray,
            focusedBorderColor = Teal,
            unfocusedBorderColor = Teal,
            cursorColor = Teal,
        )
    )
}

@Composable
private fun TempButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFFF2F2F2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = DarkText
        )
    }
}

@Composable
private fun RepeatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) TealLight else Color(0xFFF7F7F7))
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Teal else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) Teal else GrayText
        )
    }
}