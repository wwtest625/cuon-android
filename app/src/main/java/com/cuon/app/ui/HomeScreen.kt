package com.cuon.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.theme.PriorityHigh
import com.cuon.app.ui.theme.PriorityLow
import com.cuon.app.ui.theme.PriorityMedium
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<TaskEntity>,
    isProcessingAi: Boolean,
    onToggleTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onVoiceInputClick: () -> Unit,
    onTextInputSubmit: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    var showCompletedSection by remember { mutableStateOf(false) }

    val calendarEvents = remember(tasks) { tasks.filter { it.isCalendarEvent } }
    val pendingTodos = remember(tasks) { tasks.filter { !it.isCalendarEvent && !it.isCompleted } }
    val completedTodos = remember(tasks) { tasks.filter { !it.isCalendarEvent && it.isCompleted } }

    val demoPresets = listOf(
        "下周三下午两点在国贸跟李总聊外贸合同，下周五前把修改草案发他，另外顺便提醒我买两盒咖啡豆",
        "明天上午十点全员季度例会，今晚八点前提交上周工作周报，紧急联系法务审查保密协议",
        "后天下午三点去机场接张教授，预定国宾酒店两间大床房，周日晚上八点聚餐"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "C",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Cuon AI 工作台",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "2.5 Flash",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "待办 ${pendingTodos.size} 项 · 日程 ${calendarEvents.size} 场",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (completedTodos.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCompletedSection = !showCompletedSection
                        }) {
                            Icon(
                                imageVector = if (showCompletedSection) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "切换已完成",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 快捷免真机测试提示芯片条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    demoPresets.forEachIndexed { index, preset ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTextInputSubmit(preset)
                            },
                            label = {
                                Text(
                                    text = "⚡ 示例 ${index + 1}: ${preset.take(11)}...",
                                    fontSize = 11.sp
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // 常驻底部悬浮交互胶囊
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVoiceInputClick()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "语音输入",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("说出或粘贴杂乱事项，AI 自动拆解...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            enabled = !isProcessingAi
                        )

                        if (isProcessingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(4.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTextInputSubmit(textInput)
                                    textInput = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "提交",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 日程模块
            if (calendarEvents.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 确定排期日程 (${calendarEvents.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "向左滑动删除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                items(calendarEvents, key = { "cal_${it.id}" }) { event ->
                    SwipeDismissItem(
                        onDismiss = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTask(event)
                        }
                    ) {
                        CalendarCard(event = event)
                    }
                }
            }

            // 2. 待办任务清单
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✅ 待办行动事项 (${pendingTodos.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "点击勾选 · 震动反馈",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            if (pendingTodos.isEmpty() && calendarEvents.isEmpty()) {
                item {
                    ModernEmptyState()
                }
            } else {
                items(pendingTodos, key = { "todo_${it.id}" }) { task ->
                    SwipeDismissItem(
                        onDismiss = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTask(task)
                        }
                    ) {
                        TaskCard(
                            task = task,
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleTask(task)
                            }
                        )
                    }
                }
            }

            // 3. 已完成任务（支持折叠）
            if (completedTodos.isNotEmpty() && showCompletedSection) {
                item {
                    Text(
                        text = "🎉 已达成目标 (${completedTodos.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                items(completedTodos, key = { "done_${it.id}" }) { task ->
                    SwipeDismissItem(
                        onDismiss = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTask(task)
                        }
                    ) {
                        TaskCard(
                            task = task,
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleTask(task)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手势向左滑动删除容器 (Swipe-to-Dismiss)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDismissItem(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val progress = dismissState.progress
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.scale(1f + (progress * 0.2f).coerceAtMost(0.4f))
                    )
                    Text(
                        text = "滑动删除",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) {
        content()
    }
}

/**
 * 现代化日程卡片 (Calendar Card) - 100% 对齐设计稿
 */
@Composable
fun CalendarCard(event: TaskEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧优先级指示竖胶囊
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (event.priority) {
                            "high" -> PriorityHigh
                            "low" -> PriorityLow
                            else -> PriorityMedium
                        }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatSmartDateTime(event.startTime, event.endTime),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (event.location.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 右侧相对倒计时微胶囊 (对齐设计稿: "4天后" / "明天")
            val relativeBadge = getRelativeBadgeText(event.startTime)
            if (relativeBadge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = relativeBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * 现代化待办任务卡片 (Task Card) - 100% 对齐设计稿
 */
@Composable
fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.endTime != null && !task.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "截止: ${formatSmartDeadline(task.endTime)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (task.tag.isNotBlank()) {
                        Text(
                            text = "#${task.tag}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // 右侧高优先级指示红旗 (对齐设计稿)
            if (task.priority == "high" && !task.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = "高优先级",
                    tint = PriorityHigh,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp)
                )
            } else if (task.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp)
                )
            }
        }
    }
}

/**
 * 现代化空状态引导卡片
 */
@Composable
fun ModernEmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "今天心智自由，无待办事项",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击下方快速示例，或用麦克风倾倒零碎杂事，AI 自动拆解",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getRelativeBadgeText(startTime: Long?): String {
    if (startTime == null) return ""
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = startTime }

    val diffDays = (target.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR))
    return when {
        diffDays == 0 -> "今天"
        diffDays == 1 -> "明天"
        diffDays == 2 -> "后天"
        diffDays in 3..7 -> "${diffDays}天后"
        diffDays > 7 -> "未来"
        else -> ""
    }
}

fun formatSmartDateTime(startTime: Long?, endTime: Long?): String {
    if (startTime == null) return "今日待安排"
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = startTime }

    val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    val isTomorrow = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) + 1 == target.get(Calendar.DAY_OF_YEAR)

    val timeOnly = SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(startTime))
    val endOnly = if (endTime != null) " - " + SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(endTime)) else ""

    return when {
        isSameDay -> "今天 $timeOnly$endOnly"
        isTomorrow -> "明天 $timeOnly$endOnly"
        else -> SimpleDateFormat("MM月dd日 EEE HH:mm", Locale.CHINESE).format(Date(startTime)) + endOnly
    }
}

fun formatSmartDeadline(endTime: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = endTime }

    val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val isTomorrow = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) + 1 == target.get(Calendar.DAY_OF_YEAR)

    val timeOnly = SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(endTime))

    return when {
        endTime < System.currentTimeMillis() -> "已逾期"
        isSameDay -> "今天 $timeOnly 前"
        isTomorrow -> "明天 $timeOnly 前"
        else -> SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE).format(Date(endTime))
    }
}
