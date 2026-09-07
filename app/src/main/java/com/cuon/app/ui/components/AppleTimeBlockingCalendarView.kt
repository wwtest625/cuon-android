package com.cuon.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.AppleSwipeDismissItem
import com.cuon.app.ui.AppleTaskCard
import com.cuon.app.ui.theme.AppleBlue
import com.cuon.app.ui.theme.AppleRed
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * 王自如式 Apple 极简风格时间块日历 (Time Blocking Calendar View)
 * 包含：
 * 1. 纵向 07:00-23:00 小时网格与起止时间块 (Time Blocks)
 * 2. 贪心重叠分列算法 (左右并排，绝不重叠遮挡)
 * 3. 当天 "现在时刻" 红色刻度指示线 (每 30s 动态走动)
 * 4. 进行中日程高亮呼吸边框、过去日程降透明度 (0.52f)
 * 5. 待办截止时间红旗标记 (Deadlines)
 * 6. 轴底部 "今日待安排 / 弹性待办" 折叠收纳区 (无时间待办不遗漏)
 * 7. 点击/长按时间块弹出 Apple 极简 BottomSheet，提供删除日程与快捷编辑 (解决 P1)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleTimeBlockingCalendarView(
    modifier: Modifier = Modifier,
    selectedDate: Calendar,
    events: List<TaskEntity>,
    pendingTodos: List<TaskEntity>,
    taskGenerations: Map<Long, Int> = emptyMap(),
    onToggleTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onUpdateTask: (TaskEntity) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // 选中的日程对象（用于弹出编辑与删除 BottomSheet）
    var selectedEditingEvent by remember { mutableStateOf<TaskEntity?>(null) }

    val dayStartMillis = remember(selectedDate) {
        TimelineLayoutCalculator.getStartOfDayMillis(selectedDate)
    }

    // 过滤出属于当前日期的日程
    val dayEvents = remember(events, selectedDate) {
        events.filter { event ->
            if (event.startTime == null) false
            else TimelineLayoutCalculator.isSameDay(event.startTime, selectedDate)
        }
    }

    // 过滤出属于当天的带截止时间待办
    val dayTodosWithDeadline = remember(pendingTodos, selectedDate) {
        pendingTodos.filter { todo ->
            todo.endTime != null && TimelineLayoutCalculator.isSameDay(todo.endTime, selectedDate)
        }
    }

    // 轴底部折叠区：仅收纳无截止时间的弹性待办，避免与轴上旗标重复
    val floatingTodos = remember(pendingTodos, selectedDate) {
        pendingTodos.filter { todo ->
            todo.endTime == null
        }
    }

    // 运行重叠分列算法
    val eventLayouts = remember(dayEvents, dayStartMillis) {
        TimelineLayoutCalculator.calculateEventLayouts(dayEvents, dayStartMillis)
    }

    val today = remember { Calendar.getInstance() }
    val isToday = remember(selectedDate) {
        TimelineLayoutCalculator.isSameDay(selectedDate, today)
    }

    val startHour = TimelineLayoutCalculator.DEFAULT_START_HOUR // 7
    val endHour = TimelineLayoutCalculator.DEFAULT_END_HOUR     // 23
    val totalHours = endHour - startHour                        // 16
    val hourHeight: Dp = 62.dp
    val totalGridHeight: Dp = hourHeight * totalHours

    // 动态走动的当前分钟数 (P3: 每 30 秒自动推移红线)
    var currentMinuteOfDay by remember {
        val now = Calendar.getInstance()
        mutableStateOf(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    LaunchedEffect(isToday) {
        if (isToday) {
            while (true) {
                val now = Calendar.getInstance()
                currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                delay(30000L) // 30秒更新一次
            }
        }
    }

    // 初始化时若为当天，自动定位到当前时间或 8:00
    LaunchedEffect(selectedDate) {
        if (isToday) {
            val targetHour = (currentMinuteOfDay / 60).coerceIn(startHour, endHour)
            val scrollOffset = ((targetHour - startHour - 1).coerceAtLeast(0) * 150)
            scrollState.animateScrollTo(scrollOffset)
        } else {
            scrollState.scrollTo(0)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp), spotColor = Color(0x0C000000)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp)
        ) {
            // 1. 标题与状态栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = SimpleDateFormat("M月d日 EEE", Locale.CHINESE).format(selectedDate.time) + " 时间轴",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppleBlue.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "${dayEvents.size} 场日程 · ${dayTodosWithDeadline.size} 截止",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AppleBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.6.dp)

            // 2. 纵向小时时间轴主网格 (固定视窗 400.dp，内部平滑滚动)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(scrollState)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalGridHeight)
                ) {
                    val labelWidth: Dp = 52.dp
                    val gridWidth: Dp = maxWidth - labelWidth - 8.dp

                    // A. 绘制左侧时间刻度 + 水平标尺横线
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (hour in startHour..endHour) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(hourHeight)
                            ) {
                                Text(
                                    text = String.format("%02d:00", hour),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .width(labelWidth)
                                        .padding(start = 12.dp, top = 2.dp)
                                )

                                HorizontalDivider(
                                    modifier = Modifier
                                        .padding(start = labelWidth)
                                        .align(Alignment.TopStart),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    thickness = 0.6.dp
                                )
                            }
                        }
                    }

                    // B. 绘制日程时间块卡片 (Time Blocks with Overlap Resolution)
                    eventLayouts.forEach { layout ->
                        val task = layout.task
                        val clampedStartMin = layout.startMinute.coerceIn(startHour * 60, endHour * 60)
                        val clampedEndMin = layout.endMinute.coerceIn(startHour * 60, endHour * 60)

                        if (clampedEndMin > clampedStartMin || layout.endMinute >= startHour * 60) {
                            val durationMinutes = (clampedEndMin - clampedStartMin).coerceAtLeast(18)
                            val topOffset: Dp = hourHeight * ((clampedStartMin - startHour * 60) / 60f)
                            val blockHeight: Dp = hourHeight * (durationMinutes / 60f)

                            val colWidth: Dp = gridWidth / layout.totalCols
                            val leftOffset: Dp = labelWidth + (colWidth * layout.colIndex)

                            // 状态判定：进行中高亮、过去降透明度
                            val nowMillis = System.currentTimeMillis()
                            val isPast = if (isToday) {
                                (task.endTime ?: ((task.startTime ?: 0L) + 3600000L)) < nowMillis
                            } else {
                                selectedDate.before(today)
                            }

                            val isOngoing = isToday && (task.startTime != null && task.endTime != null &&
                                    nowMillis in task.startTime..task.endTime)

                            Box(
                                modifier = Modifier
                                    .offset(x = leftOffset, y = topOffset)
                                    .width(colWidth - 4.dp)
                                    .height(blockHeight - 2.dp)
                                    .padding(start = 2.dp, end = 2.dp)
                            ) {
                                AppleTimeBlockCard(
                                    event = task,
                                    isOngoing = isOngoing,
                                    isPast = isPast,
                                    blockHeight = blockHeight,
                                    onClick = {
                                        selectedEditingEvent = task
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedEditingEvent = task
                                    }
                                )
                            }
                        }
                    }

                    // C. 待办事项截止时间红旗标记 (Deadlines)
                    dayTodosWithDeadline.forEach { todo ->
                        val deadlineMin = TimelineLayoutCalculator.getMinuteOfDay(todo.endTime!!, dayStartMillis)
                        if (deadlineMin in (startHour * 60)..(endHour * 60)) {
                            val flagTop: Dp = hourHeight * ((deadlineMin - startHour * 60) / 60f)

                            Row(
                                modifier = Modifier
                                    .offset(x = labelWidth + 4.dp, y = flagTop - 10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppleRed.copy(alpha = 0.12f))
                                    .border(0.5.dp, AppleRed.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Flag,
                                    contentDescription = "截止",
                                    tint = AppleRed,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(todo.endTime))} 截止: ${todo.title}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = AppleRed,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // D. 当天 "现在时刻" 红色指示线 (Current Time Indicator)
                    if (isToday && currentMinuteOfDay in (startHour * 60)..(endHour * 60)) {
                        val currentLineTop: Dp = hourHeight * ((currentMinuteOfDay - startHour * 60) / 60f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = currentLineTop - 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(start = labelWidth - 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AppleRed)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.6.dp)
                                    .background(AppleRed)
                            )
                        }
                    }
                }
            }

            // 3. 轴底部 "今日待安排 / 弹性待办" 折叠收纳区
            var isFloatingSectionExpanded by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 0.6.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isFloatingSectionExpanded = !isFloatingSectionExpanded
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "今日待安排 / 弹性待办 (${floatingTodos.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (isFloatingSectionExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isFloatingSectionExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (floatingTodos.isEmpty()) {
                        Text(
                            text = "暂无弹性待办，可随时语音添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                        )
                    } else {
                        floatingTodos.forEach { todo ->
                            val gen = taskGenerations[todo.id] ?: 0
                            key("float_${todo.id}_$gen") {
                                AppleSwipeDismissItem(
                                    itemId = todo.id,
                                    generation = gen,
                                    onDismiss = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeleteTask(todo)
                                    }
                                ) {
                                    AppleTaskCard(
                                        task = todo,
                                        onToggle = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleTask(todo)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. 日程详情与编辑/删除 Apple BottomSheet (解决 P1 交互断层)
    selectedEditingEvent?.let { event ->
        var editTitle by remember(event.id) { mutableStateOf(event.title) }

        ModalBottomSheet(
            onDismissRequest = { selectedEditingEvent = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "日程详情与编辑",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = { selectedEditingEvent = null }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 标题修改输入框
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("事项名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 时间段展示
                val timeStr = remember(event.startTime, event.endTime) {
                    val s = if (event.startTime != null) SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(event.startTime)) else ""
                    val e = if (event.endTime != null) {
                        val isCross = event.startTime != null && !TimelineLayoutCalculator.isSameDay(
                            Calendar.getInstance().apply { timeInMillis = event.startTime },
                            Calendar.getInstance().apply { timeInMillis = event.endTime }
                        )
                        if (isCross) "次日 " + SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(event.endTime))
                        else SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(event.endTime))
                    } else ""
                    if (s.isNotBlank() && e.isNotBlank()) "$s - $e" else s
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "起止时间: $timeStr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (event.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "地点: ${event.location}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 红色删除按钮
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTask(event)
                            selectedEditingEvent = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleRed.copy(alpha = 0.12f),
                            contentColor = AppleRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除日程", fontWeight = FontWeight.SemiBold)
                    }

                    // 保存修改按钮
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (editTitle.isNotBlank() && editTitle != event.title) {
                                onUpdateTask(event.copy(title = editTitle.trim()))
                            }
                            selectedEditingEvent = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存修改", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Apple 风格纵向时间块卡片 (Time Block Card)
 * 实心高对比卡片 + 3.5dp 左侧主题装饰条 + 起止时间与地点
 * 支持长按与单击弹出 BottomSheet 进行编辑与删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleTimeBlockCard(
    event: TaskEntity,
    isOngoing: Boolean,
    isPast: Boolean,
    blockHeight: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // P3: 跨午夜日程标注 "至次日 HH:mm"
    val timeText = remember(event.startTime, event.endTime) {
        val s = if (event.startTime != null) SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(event.startTime)) else ""
        val e = if (event.endTime != null) {
            val isCross = event.startTime != null && !TimelineLayoutCalculator.isSameDay(
                Calendar.getInstance().apply { timeInMillis = event.startTime },
                Calendar.getInstance().apply { timeInMillis = event.endTime }
            )
            if (isCross) "至次日 " + SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(event.endTime))
            else SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(event.endTime))
        } else ""
        if (s.isNotBlank() && e.isNotBlank()) "$s - $e" else s
    }

    val cardBg = when {
        isOngoing -> AppleBlue.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isOngoing -> BorderStroke(1.2.dp, AppleBlue.copy(alpha = pulseAlpha))
        else -> BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
    }

    // P3: 矮块保护（高度 < 36.dp 时只显示单行标题，避免挤压截断）
    val isCompactHeight = blockHeight < 36.dp

    Card(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isPast) 0.52f else 1.0f)
            .shadow(if (isOngoing) 3.dp else 1.dp, RoundedCornerShape(10.dp), spotColor = Color(0x0F000000))
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = borderStroke
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.5.dp)
                    .background(if (isOngoing) AppleBlue else MaterialTheme.colorScheme.primary)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = if (isCompactHeight) 2.dp else 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isCompactHeight) 11.sp else 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isCompactHeight) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isOngoing) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (event.location.isNotBlank()) {
                            Text(
                                text = "· ${event.location}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
