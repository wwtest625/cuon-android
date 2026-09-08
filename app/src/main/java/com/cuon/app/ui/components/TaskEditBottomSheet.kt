package com.cuon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.reminder.RecurrenceHelper
import com.cuon.app.data.remote.AmapPoiService
import com.cuon.app.util.openInAmap
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val AppleRed = Color(0xFFFF3B30)
private val AppleBlue = Color(0xFF007AFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditBottomSheet(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    var editTitle by remember(task.id) { mutableStateOf(task.title) }
    var editDescription by remember(task.id) { mutableStateOf(task.description) }
    var editLocation by remember(task.id) { mutableStateOf(task.location) }
    var editStartTime by remember(task.id) { mutableStateOf(task.startTime) }
    var editEndTime by remember(task.id) { mutableStateOf(task.endTime) }
    // 时间两步选择状态机: date_start → time_start / date_end → time_end
    var timePickStage by remember(task.id) { mutableStateOf<String?>(null) }
    var pickedDateMillis by remember { mutableStateOf(0L) }
    var isCalendarEvent by remember(task.id) { mutableStateOf(task.isCalendarEvent) }
    var selectedTag by remember(task.id) { mutableStateOf(task.tag.ifBlank { "家庭" }) }
    var reminderMinutes by remember(task.id) { mutableStateOf(task.reminderMinutesBefore) }
    var recurrenceRule by remember(task.id) { mutableStateOf(task.recurrenceRule) }
    var isHighPriority by remember(task.id) { mutableStateOf(task.priority.equals("high", ignoreCase = true)) }

    // 地点内联联想：用户实际输入时防抖搜索高德 POI，下拉点选回填
    // (不用 snapshotFlow:打开面板时 editLocation 带有存量值,会误触发自动弹出)
    val context = LocalContext.current
    val poiService = remember { AmapPoiService() }
    val scope = rememberCoroutineScope()
    var locationSuggestions by remember { mutableStateOf<List<AmapPoiService.PoiResult>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var locationSearchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun onLocationInput(input: String) {
        locationSearchJob?.cancel()
        if (input.isBlank()) {
            locationSuggestions = emptyList()
            showSuggestions = false
            return
        }
        showSuggestions = true
        locationSearchJob = scope.launch {
            delay(350) // debounce：连输只发最后一次
            locationSuggestions = try {
                poiService.searchPois(input)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lifeTags = listOf("家庭", "健康", "纪念", "孩子", "长辈", "日常", "工作")
    val reminderOptions = listOf(
        null to "不提醒",
        0 to "准时",
        15 to "提前15分钟",
        30 to "提前30分钟"
    )
    val recurrenceOptions = listOf(
        null to "不重复",
        RecurrenceHelper.DAILY to "每天",
        RecurrenceHelper.WEEKLY to "每周",
        RecurrenceHelper.MONTHLY to "每月"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 顶栏：标题与关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCalendarEvent) "编辑生活日程" else "编辑待办事项",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 类型切换：硬性日程 vs 弹性待办
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCalendarEvent) AppleBlue else Color.Transparent)
                        .clickable { isCalendarEvent = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📅 硬性日程 (定点)",
                        fontSize = 13.sp,
                        fontWeight = if (isCalendarEvent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCalendarEvent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isCalendarEvent) AppleBlue else Color.Transparent)
                        .clickable { isCalendarEvent = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📝 生活待办 (弹性)",
                        fontSize = 13.sp,
                        fontWeight = if (!isCalendarEvent) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isCalendarEvent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 事项标题输入框
            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text("事项名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 地点输入框：单框内联联想 + 高德快捷跳转
            Box {
                OutlinedTextField(
                    value = editLocation,
                    onValueChange = {
                        editLocation = it
                        onLocationInput(it)
                    },
                    label = { Text("地点 (选填)") },
                    placeholder = { Text("输入关键词搜索地址") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = AppleBlue)
                    },
                    trailingIcon = {
                        if (editLocation.isNotBlank()) {
                            IconButton(onClick = { openInAmap(context, editLocation) }) {
                                Icon(
                                    Icons.Outlined.Navigation,
                                    contentDescription = "在高德地图打开",
                                    tint = AppleBlue
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                // POI 联想下拉
                DropdownMenu(
                    expanded = showSuggestions && locationSuggestions.isNotEmpty(),
                    onDismissRequest = { showSuggestions = false },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 280.dp)
                ) {
                    locationSuggestions.take(6).forEach { poi ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = poi.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = poi.toDisplayAddress()
                                    if (sub != poi.name) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                editLocation = poi.toDisplayAddress()
                                showSuggestions = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 备注输入框：补充细节（挂号科室、礼物偏好、注意事项等）
            OutlinedTextField(
                value = editDescription,
                onValueChange = { editDescription = it },
                label = { Text("备注 (选填)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 排期时间（点击修改：先选日期、再选时刻）
            if (editStartTime != null || editEndTime != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "排期时间 (点击修改)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val startText = editStartTime
                            if (startText != null) {
                                Text(
                                    text = "开始 " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE).format(Date(startText)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier
                                        .clickable { timePickStage = "date_start" }
                                        .padding(vertical = 2.dp)
                                )
                            }
                            val endText = editEndTime
                            if (endText != null) {
                                Text(
                                    text = "截止 " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE).format(Date(endText)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier
                                        .clickable { timePickStage = "date_end" }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 提醒选项选择
            Text(
                text = "定时提醒设置 (到点通知，绝不错过)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reminderOptions.forEach { (mins, label) ->
                    val isSelected = reminderMinutes == mins
                    FilterChip(
                        selected = isSelected,
                        onClick = { reminderMinutes = mins },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppleBlue) }
                        } else null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 重复规则选择（完成后自动生成下一个周期实例）
            Text(
                text = "重复设置 (完成后自动排下一次)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recurrenceOptions.forEach { (rule, label) ->
                    val isSelected = recurrenceRule == rule
                    FilterChip(
                        selected = isSelected,
                        onClick = { recurrenceRule = rule },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Outlined.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppleBlue) }
                        } else null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 生活分类标签
            Text(
                text = "生活分类",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lifeTags.forEach { tag ->
                    val isSelected = selectedTag == tag
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTag = tag },
                        label = { Text("#$tag", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 优先级
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = null,
                        tint = if (isHighPriority) AppleRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "标为重要生活事项", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = isHighPriority,
                    onCheckedChange = { isHighPriority = it }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 底部操作区：删除 / 保存
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDelete(task)
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除事项")
                }

                Button(
                    onClick = {
                        val s = editStartTime
                        val e = editEndTime
                        if (s != null && e != null && s > e) {
                            Toast.makeText(context, "开始时间不能晚于截止时间", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (editTitle.isNotBlank()) {
                            val updated = task.copy(
                                title = editTitle.trim(),
                                description = editDescription.trim(),
                                location = editLocation.trim(),
                                startTime = editStartTime,
                                endTime = editEndTime,
                                isCalendarEvent = isCalendarEvent,
                                tag = selectedTag,
                                reminderMinutesBefore = reminderMinutes,
                                recurrenceRule = recurrenceRule,
                                priority = if (isHighPriority) "high" else "medium"
                            )
                            onSave(updated)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存修改", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // DatePicker 返回 UTC 零点，需转本地日历年月日再套所选拍点
    fun combineLocalDateAndTime(dateMillisUtc: Long, hour: Int, minute: Int): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillisUtc }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    timePickStage?.let { stage ->
        if (stage == "date_start" || stage == "date_end") {
            val target = if (stage == "date_start") editStartTime else editEndTime
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = target)
            DatePickerDialog(
                onDismissRequest = { timePickStage = null },
                confirmButton = {
                    TextButton(onClick = {
                        val sel = datePickerState.selectedDateMillis
                        if (sel != null) {
                            pickedDateMillis = sel
                            timePickStage = if (stage == "date_start") "time_start" else "time_end"
                        }
                    }) { Text("下一步") }
                },
                dismissButton = {
                    TextButton(onClick = { timePickStage = null }) { Text("取消") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        } else {
            val target = if (stage == "time_start") editStartTime else editEndTime
            val cal = Calendar.getInstance().apply { target?.let { timeInMillis = it } }
            val timeState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { timePickStage = null },
                confirmButton = {
                    TextButton(onClick = {
                        val combined = combineLocalDateAndTime(pickedDateMillis, timeState.hour, timeState.minute)
                        if (stage == "time_start") editStartTime = combined else editEndTime = combined
                        timePickStage = null
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { timePickStage = null }) { Text("取消") }
                },
                title = { Text("选择时刻") },
                text = { TimePicker(state = timeState) }
            )
        }
    }
}
