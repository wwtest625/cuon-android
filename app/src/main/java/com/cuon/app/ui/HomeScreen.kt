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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.components.AppleCalendarView
import com.cuon.app.ui.components.AppleTimeBlockingCalendarView
import com.cuon.app.ui.components.ColorfulVoiceWaveform
import com.cuon.app.ui.components.SkeletonGhostCard

import com.cuon.app.ui.components.StreamingTypewriterCard
import com.cuon.app.ui.components.ThanosSnapDisintegration

import com.cuon.app.ui.theme.*


import com.cuon.app.ui.components.AiDraftPreviewBottomSheet
import com.cuon.app.ui.components.TaskEditBottomSheet
import com.cuon.app.util.SpeechRecognitionManager
import com.cuon.app.util.openInAmap
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<TaskEntity>,
    isProcessingAi: Boolean,
    taskGenerations: Map<Long, Int> = emptyMap(),
    draftTasks: List<TaskEntity>? = null,
    editingTask: TaskEntity? = null,
    initialSelectedDate: Long? = null,
    onToggleTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onUpdateTask: (TaskEntity) -> Unit = {},
    onEditTask: (TaskEntity) -> Unit = {},
    onCloseTaskEditor: () -> Unit = {},
    onSaveEditedTask: (TaskEntity) -> Unit = {},
    onDismissDraft: () -> Unit = {},
    onConfirmDraft: (List<TaskEntity>) -> Unit = {},
    onUpdateDraftItem: (Int, TaskEntity) -> Unit = { _, _ -> },
    onRemoveDraftItem: (Int) -> Unit = {},
    onVoiceResult: (String) -> Unit,
    onTextInputSubmit: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    var showCompletedSection by remember { mutableStateOf(false) }

    // 系统级语音听写（直连厂商识别引擎：荣耀 MagicVoice / 小爱 / 讯飞等，无需谷歌服务）
    val context = LocalContext.current
    val speechManager = remember { SpeechRecognitionManager(context) }
    val speechState by speechManager.uiState.collectAsState()
    var isHoldingMic by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        speechManager.onFinalResult = { spokenText ->
            isHoldingMic = false
            if (spokenText != null) {
                onVoiceResult(spokenText)
            } else if (speechState.errorMessage.isNotBlank()) {
                android.widget.Toast.makeText(context, speechState.errorMessage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        onDispose {
            speechManager.destroy()
            speechManager.onFinalResult = null
        }
    }

    val calendarEvents = remember(tasks) { tasks.filter { it.isCalendarEvent } }
    val pendingTodos = remember(tasks) { tasks.filter { !it.isCalendarEvent && !it.isCompleted } }
    val completedTodos = remember(tasks) { tasks.filter { !it.isCalendarEvent && it.isCompleted } }

    var selectedCalendarDate by remember { mutableStateOf<Calendar?>(null) }

    // 标签筛选:点击卡片上的 #标签 进入筛选态,再点同一个或点 chip 的 ✕ 取消
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }

    // 处理深链跳转指定日期
    LaunchedEffect(initialSelectedDate) {
        if (initialSelectedDate != null && initialSelectedDate > 0) {
            selectedCalendarDate = Calendar.getInstance().apply { timeInMillis = initialSelectedDate }
        }
    }

    val displayCalendarEvents = remember(calendarEvents, selectedCalendarDate, selectedTagFilter) {
        val target = selectedCalendarDate
        val base = if (target == null) calendarEvents
        else {
            calendarEvents.filter { event ->
                if (event.startTime == null) false
                else {
                    val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                    cal.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
                }
            }
        }
        if (selectedTagFilter != null) base.filter { it.tag == selectedTagFilter } else base
    }

    // 标签筛选后的待办/已完成列表
    val filteredPendingTodos = remember(pendingTodos, selectedTagFilter) {
        if (selectedTagFilter == null) pendingTodos else pendingTodos.filter { it.tag == selectedTagFilter }
    }
    val filteredCompletedTodos = remember(completedTodos, selectedTagFilter) {
        if (selectedTagFilter == null) completedTodos else completedTodos.filter { it.tag == selectedTagFilter }
    }

    // 最新入场的新任务支持流式打字机逐字填字

    val streamingTaskIds = remember { mutableStateListOf<Long>() }
    var previousTaskIds by remember { mutableStateOf(tasks.map { it.id }.toSet()) }
    val sessionStart = remember { System.currentTimeMillis() }

    LaunchedEffect(tasks) {
        val currentIds = tasks.map { it.id }.toSet()
        val newIds = currentIds - previousTaskIds
        if (newIds.isNotEmpty()) {
            // 只对本次会话新建的任务放特效;冷启动从数据库加载的存量任务不复播
            val freshIds = tasks.filter { it.id in newIds && it.createdAt >= sessionStart - 2000 }.map { it.id }
            streamingTaskIds.addAll(freshIds)
        }
        previousTaskIds = currentIds
    }

    val demoPresets = listOf(

        "下周六上午九点陪爸妈去中心医院体检，周五前记得电话预约挂号，另外买一箱牛奶看望他们",
        "女儿9月20日下午三点家长会，提前一天准备好要问老师的问题，下周二是结婚纪念日，订个蛋糕买束花",
        "周三晚上七点半健身房私教课别忘了，这个月内必须缴物业费和车险，周末抽空带狗狗去打疫苗"
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
                                    text = "Cuon 生活事项",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppleBlue.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "智能守护",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
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
                                .alpha(if (isProcessingAi) 0.45f else 1f)
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

                // 2. 常驻底部 Apple 极简悬浮输入胶囊（支持按住变身炫彩波浪舱）
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(
                            elevation = if (isHoldingMic) 10.dp else 6.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = if (isHoldingMic) Color(0x33007AFF) else Color(0x14000000)
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isHoldingMic) {
                        BorderStroke(
                            1.5.dp,
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(AppleBlue, ApplePurple, ApplePink, AppleTeal)
                            )
                        )
                    } else {
                        BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                    }
                ) {
                    AnimatedContent(
                        targetState = isHoldingMic,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) + expandVertically() togetherWith
                                    fadeOut(animationSpec = tween(180)) + shrinkVertically()
                        },
                        label = "inputCapsuleMode"
                    ) { holding ->
                        if (holding) {
                            // 🌟 真实语音聆听舱：按住即录音，松开即识别发送（直连厂商识别引擎）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),

                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(AppleRed)
                                        )
                                        Text(
                                            text = speechState.partialText.ifBlank { "正在聆听，请说出您的生活安排…（松开结束）" },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = AppleBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = "松开发送",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = AppleBlue
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 炫彩流体声波：振幅绑定真实麦克风音量 (RMS)
                                com.cuon.app.ui.components.ColorfulVoiceWaveform(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    isListening = true,
                                    amplitudeMultiplier = (0.3f + speechState.rmsDb / 8f).coerceIn(0.15f, 1.4f)
                                )
                            }
                        } else {

                            // 极简输入条模式
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 麦克风按钮：按下立即真实录音，松开立即结束并发送
                                AppleHoldingPulsingMicButton(
                                    isProcessing = isProcessingAi,
                                    onPressStart = {
                                        isHoldingMic = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        speechManager.startListening()
                                    },
                                    onPressEnd = {
                                        if (isHoldingMic) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            speechManager.finishListening()
                                        }
                                    },

                                    onClick = {
                                        // 极短按视同"按下-松开"完整周期（onPress/onRelease 已覆盖）
                                    }
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                TextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    placeholder = {
                                        Text(
                                            text = "按住说话，或记录生活安排、家庭日程...",
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
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                                )

                                if (textInput.isNotBlank()) {
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
                                } else if (isProcessingAi) {
                                    // AI 处理中仍保持可输入，仅无文字时展示进度提示（不再全锁交互）
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .padding(4.dp),
                                        strokeWidth = 2.dp,
                                        color = AppleBlue
                                    )
                                }
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
            // 0. 原生 Apple 极简线条日历组件

            item(key = "apple_calendar_component") {
                AppleCalendarView(
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    events = calendarEvents,
                    selectedDate = selectedCalendarDate,
                    onDateSelected = { date ->
                        selectedCalendarDate = date
                    }
                )
            }

            // 标签筛选激活态 chip
            if (selectedTagFilter != null) {
                item(key = "tag_filter_chip") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppleBlue.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "标签 #${selectedTagFilter}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = AppleBlue,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "清除标签筛选",
                                tint = AppleBlue,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { selectedTagFilter = null }
                                    .padding(3.dp)
                            )
                        }
                    }
                }
            }

            // 王自如式时间块日历：点击某天无缝展开纵向 07:00-23:00 小时时间轴 (V1)
            if (selectedCalendarDate != null) {
                item(key = "time_blocking_timeline_${selectedCalendarDate!!.timeInMillis}") {
                    AppleTimeBlockingCalendarView(
                        modifier = Modifier.padding(bottom = 6.dp),
                        selectedDate = selectedCalendarDate!!,
                        events = calendarEvents,
                        pendingTodos = pendingTodos,
                        taskGenerations = taskGenerations,
                        onToggleTask = onToggleTask,
                        onDeleteTask = onDeleteTask,
                        onUpdateTask = onUpdateTask,
                        onEditTask = onEditTask
                    )
                }
            }

            // 1. 日程模块（在全部视角或 AI 处理时展示）
            if (selectedCalendarDate == null && (displayCalendarEvents.isNotEmpty() || isProcessingAi)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "排期日程",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // ⚡ 0 毫秒即时响应：AI 思考排期时的微光流体骨架卡片
                if (isProcessingAi) {
                    item(key = "skeleton_cal_card") {
                        SkeletonGhostCard(isCalendar = true)
                    }
                }

                items(displayCalendarEvents, key = { "cal_${it.id}_${taskGenerations[it.id] ?: 0}" }) { event ->

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
                            itemId = event.id,
                            generation = taskGenerations[event.id] ?: 0,
                            onDismiss = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteTask(event)
                            }
                        ) {
                            if (event.id in streamingTaskIds) {
                                // ✨ 流式打字机逐字填字效果
                                StreamingTypewriterCard(
                                    item = event,
                                    onFinishTyping = {
                                        streamingTaskIds.remove(event.id)
                                    }
                                )
                            } else {
                                AppleCalendarCard(
                                    event = event,
                                    onClick = { onEditTask(event) }
                                )
                            }
                        }
                    }
                }
            }

            // 2. 待办任务清单（全部视角）
            if (selectedCalendarDate == null) {
                item {
                    Text(
                        text = "生活待办",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp)
                    )
                }

                // ⚡ 0 毫秒即时响应：AI 待办意图拆解时的微光流体骨架卡片
                if (isProcessingAi) {
                    item(key = "skeleton_todo_card") {
                        SkeletonGhostCard(isCalendar = false)
                    }
                }

                if (filteredPendingTodos.isEmpty() && displayCalendarEvents.isEmpty() && !isProcessingAi) {
                    item {
                        AppleEmptyState()
                    }
                } else {
                    items(filteredPendingTodos, key = { "todo_${it.id}_${taskGenerations[it.id] ?: 0}" }) { task ->
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
                            itemId = task.id,
                            generation = taskGenerations[task.id] ?: 0,
                            onDismiss = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteTask(task)
                            }
                        ) {
                            if (task.id in streamingTaskIds) {
                                // ✨ 流式打字机逐字填字效果
                                StreamingTypewriterCard(
                                    item = task,
                                    onFinishTyping = {
                                        streamingTaskIds.remove(task.id)
                                    }
                                )
                            } else {
                                AppleTaskCard(
                                    task = task,
                                    onToggle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleTask(task)
                                    },
                                    onClick = { onEditTask(task) },
                                    onTagClick = { tag ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTagFilter = if (selectedTagFilter == tag) null else tag
                                    }
                                )
                            }
                        }
                    }
                }
            }


            // 3. 已完成任务
            if (filteredCompletedTodos.isNotEmpty() && showCompletedSection) {
                item {
                    Text(
                        text = "已完成 (${filteredCompletedTodos.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
                items(filteredCompletedTodos, key = { "done_${it.id}_${taskGenerations[it.id] ?: 0}" }) { task ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        AppleSwipeDismissItem(
                            itemId = task.id,
                            generation = taskGenerations[task.id] ?: 0,
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
                                },
                                onClick = { onEditTask(task) },
                                onTagClick = { tag ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTagFilter = if (selectedTagFilter == tag) null else tag
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // AI 拆解预览确认 BottomSheet (方案 A：核对纠错后再批量入库)
    draftTasks?.let { drafts ->
        AiDraftPreviewBottomSheet(
            draftItems = drafts,
            onDismiss = onDismissDraft,
            onConfirmAll = { confirmedList ->
                onConfirmDraft(confirmedList)
            },
            onItemUpdate = { index, updated ->
                onUpdateDraftItem(index, updated)
            },
            onItemRemove = { index ->
                onRemoveDraftItem(index)
            }
        )
    }

    // 全局通用任务/日程编辑 BottomSheet
    editingTask?.let { task ->
        TaskEditBottomSheet(
            task = task,
            onDismiss = onCloseTaskEditor,
            onSave = { updated ->
                onSaveEditedTask(updated)
            },
            onDelete = {
                onDeleteTask(it)
                onCloseTaskEditor()
            }
        )
    }
    }
}

/**
 * 麦克风按钮 (支持按住变身炫彩波浪 & 呼吸脉冲光环)
 */
@Composable
fun AppleHoldingPulsingMicButton(
    isProcessing: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
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

    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "buttonPressScale"
    )

    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        // 动态呼吸脉冲环
        if (isProcessing || isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(AppleBlue.copy(alpha = pulseAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(AppleBlue)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            // 微信式直觉交互：按下立即开始录音，松开立即结束并发送
                            isPressed = true
                            onPressStart()
                            tryAwaitRelease()
                            isPressed = false
                            onPressEnd()
                        }
                    )
                },


            contentAlignment = Alignment.Center
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
 * Apple 风格左滑删除容器 (Swipe-to-Dismiss) + 灭霸打响指粒子消散特效
 * 彻底消除文字重叠：卡片本身 100% 实心白色不透明；触发删除时如灭霸打响指般化为飞沙烟尘消散
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleSwipeDismissItem(
    itemId: Long = 0L,
    generation: Int = 0,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSnapping by remember(itemId, generation) { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                isSnapping = true
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.38f }
    )

    // 解决 P1 红色残影：撤销恢复 (generation 变更) 或组件重建时，强制复位滑动状态与消散动效
    LaunchedEffect(itemId, generation) {
        isSnapping = false
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    ThanosSnapDisintegration(
        isDisintegrating = isSnapping,
        onDisintegrated = onDismiss
    ) {
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
}

/**
 * Apple 极简线条风格日程卡片 (Calendar Card)
 * 实心背景 + 0.6dp 极细轮廓边框 + 清晰时间与右侧胶囊，绝无文字重叠！
 */
@Composable
fun AppleCalendarCard(
    event: TaskEntity,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { openInAmap(context, event.location) }
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
    onToggle: () -> Unit,
    onClick: () -> Unit = {},
    onTagClick: (String) -> Unit = {}
) {
    var isCheckedAnim by remember(task.isCompleted) { mutableStateOf(task.isCompleted) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isCheckedAnim) 0.55f else 1.0f,
        animationSpec = tween(300),
        label = "cardAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .clickable { onClick() }
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
                checked = isCheckedAnim,
                onCheckedChange = {
                    isCheckedAnim = !isCheckedAnim
                    onToggle()
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCheckedAnim) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (isCheckedAnim) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCheckedAnim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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
                                color = AppleBlue,
                                modifier = Modifier.clickable { onTagClick(task.tag) }
                            )
                        }
                        if (task.reminderMinutesBefore != null && !task.isCompleted) {
                            val reminderLabel = if (task.reminderMinutesBefore == 0) "准时" else "提前${task.reminderMinutesBefore}分"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF34C759).copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, Color(0xFF34C759).copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "🔔 $reminderLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF34C759),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 右侧优先级指示:仅高优先级显示红拇指
            if (task.priority == "high" && !task.isCompleted) {
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = "高优先级",
                    tint = PriorityHigh,
                    modifier = Modifier.size(15.dp)
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
                text = "今日生活安排已就绪",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "暂无待办事项，尽情享受轻松惬意的当下时光",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getDaysDiffFromToday(targetMillis: Long): Long {
    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val target = Calendar.getInstance().apply {
        timeInMillis = targetMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (target.timeInMillis - now.timeInMillis) / (24 * 3600 * 1000L)
}

fun getRelativeBadgeText(startTime: Long?): String {
    if (startTime == null) return ""
    val diffDays = getDaysDiffFromToday(startTime)
    return when {
        diffDays == 0L -> "今天"
        diffDays == 1L -> "明天"
        diffDays == 2L -> "后天"
        diffDays in 3L..7L -> "${diffDays}天后"
        diffDays > 7L -> "未来"
        diffDays == -1L -> "昨天"
        diffDays < -1L -> "已过去"
        else -> ""
    }
}

fun formatSmartDateTime(startTime: Long?, endTime: Long?): String {
    if (startTime == null) return "今日待安排"
    val diffDays = getDaysDiffFromToday(startTime)

    val timeOnly = SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(startTime))
    val endOnly = if (endTime != null) " - " + SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(endTime)) else ""

    return when (diffDays) {
        0L -> "今天 $timeOnly$endOnly"
        1L -> "明天 $timeOnly$endOnly"
        2L -> "后天 $timeOnly$endOnly"
        else -> SimpleDateFormat("MM月dd日 EEE HH:mm", Locale.CHINESE).format(Date(startTime)) + endOnly
    }
}

fun formatSmartDeadline(endTime: Long): String {
    val diffDays = getDaysDiffFromToday(endTime)
    val timeOnly = SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(endTime))

    return when {
        endTime < System.currentTimeMillis() -> "已逾期"
        diffDays == 0L -> "今天 $timeOnly 前"
        diffDays == 1L -> "明天 $timeOnly 前"
        diffDays == 2L -> "后天 $timeOnly 前"
        else -> SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE).format(Date(endTime))
    }
}

