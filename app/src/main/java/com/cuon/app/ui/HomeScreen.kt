package com.cuon.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.components.AiDraftPreviewBottomSheet
import com.cuon.app.ui.components.AppleCalendarCard
import com.cuon.app.ui.components.AppleCalendarView
import com.cuon.app.ui.components.AppleEmptyState
import com.cuon.app.ui.components.AppleSwipeDismissItem
import com.cuon.app.ui.components.AppleTaskCard
import com.cuon.app.ui.components.AppleTimeBlockingCalendarView
import com.cuon.app.ui.components.HistoryListBottomSheet
import com.cuon.app.ui.components.HomeInputBar
import com.cuon.app.ui.components.HomeTopBar
import com.cuon.app.ui.components.SkeletonGhostCard
import com.cuon.app.ui.components.StreamingTypewriterCard
import com.cuon.app.ui.components.TaskEditBottomSheet
import com.cuon.app.ui.theme.AppleBlue
import java.util.Calendar

/**
 * Cuon 首页：日历 + 日程 + 待办三分区，底部常驻语音/文字输入
 *
 * 职责收敛为「筛选派生状态 + 列表编排」，
 * 顶栏 / 底部输入栏 / 各类卡片 / 相对时间格式化均已拆至独立文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<TaskEntity>,
    trash: List<TaskEntity> = emptyList(),
    isProcessingAi: Boolean,
    taskGenerations: Map<Long, Int> = emptyMap(),
    draftTasks: List<TaskEntity>? = null,
    editingTask: TaskEntity? = null,
    initialSelectedDate: Long? = null,
    onToggleTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onRestoreTask: (TaskEntity) -> Unit = {},
    onDeleteForever: (TaskEntity) -> Unit = {},
    onUpdateTask: (TaskEntity) -> Unit = {},
    onEditTask: (TaskEntity) -> Unit = {},
    onCloseTaskEditor: () -> Unit = {},
    onSaveEditedTask: (TaskEntity) -> Unit = {},
    onDismissDraft: () -> Unit = {},
    onConfirmDraft: (List<TaskEntity>) -> Unit = {},
    onUpdateDraftItem: (Int, TaskEntity) -> Unit = { _, _ -> },
    onRemoveDraftItem: (Int) -> Unit = {},
    onVoiceResult: (String) -> Unit = {},
    onTextInputSubmit: (String) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    var selectedCalendarDate by remember { mutableStateOf<Calendar?>(null) }

    // 标签筛选:点击卡片上的 #标签 进入筛选态,再点同一个或点 chip 的 ✕ 取消
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var showCompletedSection by remember { mutableStateOf(true) }
    var showHistorySheet by remember { mutableStateOf(false) }

    // 处理深链跳转指定日期
    LaunchedEffect(initialSelectedDate) {
        if (initialSelectedDate != null && initialSelectedDate > 0) {
            selectedCalendarDate = Calendar.getInstance().apply { timeInMillis = initialSelectedDate }
        }
    }

    val calendarEvents = remember(tasks) { tasks.filter { it.isCalendarEvent && !it.isCompleted } }
    val pendingTodos = remember(tasks) { tasks.filter { !it.isCalendarEvent && !it.isCompleted } }
    // 已完成按历史归属时间倒序（待办 + 归档日程统一进历史；completedAt ?: startTime ?: endTime ?: createdAt 兜底存量）
    val completedTodos = remember(tasks) {
        tasks.filter { it.isCompleted }
            .sortedByDescending { historyAnchorMillis(it) }
    }

    // 选中某天 → 该日完成的待办（历史记录 · 日历视角）
    val dayCompletedTodos = remember(completedTodos, selectedCalendarDate, selectedTagFilter) {
        val target = selectedCalendarDate ?: return@remember emptyList()
        val byDay = completedTodos.filter { task ->
            val anchor = Calendar.getInstance().apply { timeInMillis = historyAnchorMillis(task) }
            anchor.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    anchor.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        }
        if (selectedTagFilter != null) byDay.filter { it.tag == selectedTagFilter } else byDay
    }

    // 选中某天 → 只看当天日程；否则看全部日程（可叠加标签筛选）
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(
                pendingCount = pendingTodos.size,
                calendarCount = calendarEvents.size,
                hasCompleted = completedTodos.isNotEmpty() || trash.isNotEmpty(),
                showCompleted = showCompletedSection,
                onToggleShowCompleted = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showCompletedSection = !showCompletedSection
                },
                onOpenHistory = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showHistorySheet = true
                }
            )
        },
        bottomBar = {
            val context = LocalContext.current
            HomeInputBar(
                onTextSubmit = onTextInputSubmit,
                onVoiceResult = onVoiceResult,
                onVoiceError = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                isProcessingAi = isProcessingAi
            )
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
                    onDateSelected = { date -> selectedCalendarDate = date }
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

            // 该日完成（历史记录 · 日历视角）：选中某天时展示当天完成的待办，空则隐藏不占位
            if (selectedCalendarDate != null && dayCompletedTodos.isNotEmpty()) {
                item(key = "day_completed_header") {
                    Text(
                        text = "该日完成 (${dayCompletedTodos.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
                items(dayCompletedTodos, key = { "daydone_${it.id}_${taskGenerations[it.id] ?: 0}" }) { task ->
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

            // 1. 日程模块（在全部视角或 AI 处理时展示）
            if (selectedCalendarDate == null && (displayCalendarEvents.isNotEmpty() || isProcessingAi)) {
                item {
                    Text(
                        text = "排期日程",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp)
                    )
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
                                androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.shrinkVertically(
                            spring(stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut()
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
                                    onFinishTyping = { streamingTaskIds.remove(event.id) }
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
                    item { AppleEmptyState() }
                } else {
                    items(filteredPendingTodos, key = { "todo_${it.id}_${taskGenerations[it.id] ?: 0}" }) { task ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                    slideInVertically(
                                        initialOffsetY = { 30 },
                                        animationSpec = spring(dampingRatio = 0.8f)
                                    ) +
                                    androidx.compose.animation.expandVertically(),
                            exit = androidx.compose.animation.shrinkVertically(
                                spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut()
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
                                        onFinishTyping = { streamingTaskIds.remove(task.id) }
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
            }

            // 3. 已完成任务（选中日期时隐藏，避免与"该日完成"分区信息重复）
            if (selectedCalendarDate == null && filteredCompletedTodos.isNotEmpty() && showCompletedSection) {
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
                        enter = fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.shrinkVertically() + fadeOut()
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

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // AI 拆解预览确认 BottomSheet (方案 A：核对纠错后再批量入库)
    draftTasks?.let { drafts ->
        AiDraftPreviewBottomSheet(
            draftItems = drafts,
            onDismiss = onDismissDraft,
            onConfirmAll = { confirmedList -> onConfirmDraft(confirmedList) },
            onItemUpdate = { index, updated -> onUpdateDraftItem(index, updated) },
            onItemRemove = { index -> onRemoveDraftItem(index) }
        )
    }

    // 历史记录底部弹层（另一个浏览视角：全部完成事项按日期倒序分组 + 回收站）
    if (showHistorySheet) {
        HistoryListBottomSheet(
            completedTasks = completedTodos,
            onDismiss = { showHistorySheet = false },
            onToggleTask = onToggleTask,
            onEditTask = onEditTask,
            onTagClick = { tag ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedTagFilter = if (selectedTagFilter == tag) null else tag
                showHistorySheet = false
            },
            trashedTasks = trash,
            onRestoreTask = onRestoreTask,
            onDeleteForever = onDeleteForever
        )
    }

    // 全局通用任务/日程编辑 BottomSheet
    editingTask?.let { task ->
        TaskEditBottomSheet(
            task = task,
            onDismiss = onCloseTaskEditor,
            onSave = { updated -> onSaveEditedTask(updated) },
            onDelete = {
                onDeleteTask(it)
                onCloseTaskEditor()
            }
        )
    }
}
