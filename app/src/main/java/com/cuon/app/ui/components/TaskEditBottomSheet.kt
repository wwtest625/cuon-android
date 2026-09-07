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
import com.cuon.app.data.remote.AmapPoiService
import com.cuon.app.util.openInAmap
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
    var editLocation by remember(task.id) { mutableStateOf(task.location) }
    var isCalendarEvent by remember(task.id) { mutableStateOf(task.isCalendarEvent) }
    var selectedTag by remember(task.id) { mutableStateOf(task.tag.ifBlank { "家庭" }) }
    var reminderMinutes by remember(task.id) { mutableStateOf(task.reminderMinutesBefore) }
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
                placeholder = { Text("例如：陪妈妈去同仁医院配药") },
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

            Spacer(modifier = Modifier.height(14.dp))

            // 时间展示与提醒设置
            if (task.startTime != null || task.endTime != null) {
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
                            val timeStr = buildString {
                                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
                                if (task.startTime != null) append(sdf.format(Date(task.startTime)))
                                if (task.endTime != null) {
                                    if (isNotEmpty()) append(" - ")
                                    val timeOnly = SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(task.endTime))
                                    append(timeOnly)
                                }
                            }
                            Text(text = "排期时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = timeStr, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
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
                        if (editTitle.isNotBlank()) {
                            val updated = task.copy(
                                title = editTitle.trim(),
                                location = editLocation.trim(),
                                isCalendarEvent = isCalendarEvent,
                                tag = selectedTag,
                                reminderMinutesBefore = reminderMinutes,
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
}
