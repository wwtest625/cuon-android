package com.cuon.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.theme.*
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = AppleBlue,
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "C",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Cuon 工作台",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppleBlue.copy(alpha = 0.12f),
                                    border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = "2.5 Flash",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        color = AppleBlue,
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
                                imageVector = if (showCompletedSection) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "切换已完成",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 6.dp)
            ) {
                // 1. Apple 风格快捷示例胶囊条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    demoPresets.forEachIndexed { index, preset ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTextInputSubmit(preset)
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "⚡ 示例 ${index + 1}: ${preset.take(10)}...",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // 2. 常驻底部 Apple 极简悬浮输入胶囊
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = Color(0x14000000)
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 麦克风按钮（带声波呼吸脉冲动画）
                        ApplePulsingMicButton(
                            isProcessing = isProcessingAi,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVoiceInputClick()
                            }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    text = "自然语言输入，AI 自动整理...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            enabled = !isProcessingAi,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                        )

                        if (isProcessingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(26.dp)
                                    .padding(4.dp),
                                strokeWidth = 2.dp,
                                color = AppleBlue
                            )
                        } else if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTextInputSubmit(textInput)
                                    textInput = ""
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AppleBlue)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "提交",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
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
                    Text(
                        text = "排期日程",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
                items(calendarEvents, key = { "cal_${it.id}" }) { event ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                slideInVertically(
                                    initialOffsetY = { 30 },
                                    animationSpec = spring(dampingRatio = 0.8f)
                                ) +
                                expandVertically(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                    ) {
                        AppleSwipeDismissItem(
                            onDismiss = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteTask(event)
                            }
                        ) {
                            AppleCalendarCard(event = event)
                        }
                    }
                }
            }

            // 2. 待办任务清单
            item {
                Text(
                    text = "待办事项",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp)
                )
            }

            if (pendingTodos.isEmpty() && calendarEvents.isEmpty()) {
                item {
                    AppleEmptyState()
                }
            } else {
                items(pendingTodos, key = { "todo_${it.id}" }) { task ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                slideInVertically(
                                    initialOffsetY = { 30 },
                                    animationSpec = spring(dampingRatio = 0.8f)
                                ) +
                                expandVertically(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                    ) {
                        AppleSwipeDismissItem(
                            onDismiss = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteTask(task)
                            }
                        ) {
                            AppleTaskCard(
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

            // 3. 已完成任务
            if (completedTodos.isNotEmpty() && showCompletedSection) {
                item {
                    Text(
                        text = "已完成 (${completedTodos.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
                items(completedTodos, key = { "done_${it.id}" }) { task ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        AppleSwipeDismissItem(
                            onDismiss = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteTask(task)
                            }
                        ) {
                            AppleTaskCard(
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

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 麦克风呼吸脉冲光环按钮 (Apple 风格动画)
 */
@Composable
fun ApplePulsingMicButton(
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        // 动态呼吸脉冲环 (处理中或交互中持续绽放)
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(AppleBlue.copy(alpha = pulseAlpha))
            )
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AppleBlue)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "语音输入",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Apple 风格左滑删除容器 (Swipe-to-Dismiss)
 * 彻底消除文字重叠：卡片本身 100% 实心白色不透明，彻底隔绝底层；底层仅露出纯粹的 Apple Red + 弹性垃圾桶图标
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleSwipeDismissItem(
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
        positionalThreshold = { totalDistance -> totalDistance * 0.38f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val iconScale by animateFloatAsState(
                targetValue = if (isSwiping) 1.25f else 0.95f,
                animationSpec = spring(dampingRatio = 0.6f),
                label = "deleteIconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleRed)
                    .padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(iconScale)
                )
            }
        }
    ) {
        // 卡片内容容器：强制保持实心不透明，避免半透明透光！
        content()
    }
}

/**
 * Apple 极简线条风格日程卡片 (Calendar Card)
 * 实心背景 + 0.6dp 极细轮廓边框 + 清晰时间与右侧胶囊，绝无文字重叠！
 */
@Composable
fun AppleCalendarCard(event: TaskEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0x0A000000)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // 纯实心底色，防透视
        ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline) // 苹果极细轮廓线
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧 Apple Blue 极细圆柱指示线 (3dp × 32dp)
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(AppleBlue)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = AppleBlue
                    )
                    Text(
                        text = formatSmartDateTime(event.startTime, event.endTime),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    if (event.location.isNotBlank()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // 右侧相对时间微胶囊 (例如: "今天" / "明天" / "3天后")
            val relativeBadge = getRelativeBadgeText(event.startTime)
            if (relativeBadge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppleBlue.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = relativeBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = AppleBlue,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Apple 极简线条风格待办卡片 (Task Card)
 * 包含：微圆形打勾动效、弹力微缩放、划线褪色完成动效、极细线条边框
 */
@Composable
fun AppleTaskCard(
    task: TaskEntity,
    onToggle: () -> Unit
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.55f else 1.0f,
        animationSpec = tween(250),
        label = "cardAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0x0A000000)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // 纯实心底色，防透视
        ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline) // 苹果极细轮廓线
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Apple 原生风格圆形弹力打勾复选框
            AppleCircleCheckbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if ((task.endTime != null && !task.isCompleted) || task.tag.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.endTime != null && !task.isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppleRed.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, AppleRed.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "截止: ${formatSmartDeadline(task.endTime)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = AppleRed,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        if (task.tag.isNotBlank()) {
                            Text(
                                text = "#${task.tag}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = AppleBlue
                            )
                        }
                    }
                }
            }

            // 右侧优先级小指示
            if (task.priority == "high" && !task.isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = "高优先级",
                    tint = AppleRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Apple Reminders 风格圆形弹性复选框
 */
@Composable
fun AppleCircleCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )

    Box(
        modifier = Modifier
            .size(22.dp)
            .scale(checkScale)
            .clip(CircleShape)
            .clickable { onCheckedChange() }
            .then(
                if (checked) {
                    Modifier.background(AppleBlue)
                } else {
                    Modifier.border(BorderStroke(1.5.dp, Color(0xFFC7C7CC)), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 极简 Apple 风格空状态
 */
@Composable
fun AppleEmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = AppleBlue.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircleOutline,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "全部事项已达成",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击下方快速示例，或随时说话倾倒事项",
                style = MaterialTheme.typography.bodySmall,
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
