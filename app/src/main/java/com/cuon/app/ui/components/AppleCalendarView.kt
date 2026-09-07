package com.cuon.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.theme.AppleBlue
import java.text.SimpleDateFormat
import java.util.*

/**
 * Apple 极简线条风格周视图日历组件 (Apple Week Calendar Strip)
 * 支持：星期横向排布、今天高亮、选中联动过滤、日程微圆点标注
 */
@Composable
fun AppleCalendarView(
    modifier: Modifier = Modifier,
    events: List<TaskEntity>,
    selectedDate: Calendar?,
    onDateSelected: (Calendar?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentWeekStart by remember {
        mutableStateOf(Calendar.getInstance().apply {
            // 对齐到本周一
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    val today = remember { Calendar.getInstance() }

    // 计算当前显示的 7 天
    val weekDays = remember(currentWeekStart) {
        (0..6).map { offset ->
            (currentWeekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
        }
    }

    val monthTitle = remember(currentWeekStart) {
        SimpleDateFormat("yyyy年MM月", Locale.CHINESE).format(currentWeekStart.time)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = Color(0x0A000000)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 1. 顶部栏：月份 + 左右切换 + "今天" 胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // “今天”快速跳转胶囊
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentWeekStart = Calendar.getInstance().apply {
                                    firstDayOfWeek = Calendar.MONDAY
                                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onDateSelected(today)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = AppleBlue.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "今天",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = AppleBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // 上一周
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentWeekStart = (currentWeekStart.clone() as Calendar).apply {
                                add(Calendar.WEEK_OF_YEAR, -1)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = "上一周",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 下一周
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentWeekStart = (currentWeekStart.clone() as Calendar).apply {
                                add(Calendar.WEEK_OF_YEAR, 1)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "下一周",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 星期与日期条 (7 天对齐排列)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val weekDayNames = listOf("一", "二", "三", "四", "五", "六", "日")

                weekDays.forEachIndexed { index, dayCal ->
                    val isToday = dayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            dayCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

                    val isSelected = selectedDate != null &&
                            dayCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                            dayCal.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR)

                    // 检查这一天是否有日程安排
                    val hasEvents = events.any { event ->
                        if (event.startTime == null) false
                        else {
                            val eventCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                            dayCal.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                                    dayCal.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isSelected) {
                                    onDateSelected(null) // 再次点击取消筛选
                                } else {
                                    onDateSelected(dayCal)
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 星期名
                        Text(
                            text = weekDayNames[index],
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 日期圆形 / 胶囊高亮
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .then(
                                    when {
                                        isToday -> Modifier.background(AppleBlue)
                                        isSelected -> Modifier.background(AppleBlue.copy(alpha = 0.18f))
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${dayCal.get(Calendar.DAY_OF_MONTH)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = when {
                                    isToday -> Color.White
                                    isSelected -> AppleBlue
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // 日程指示微圆点 (Apple Blue Event Dot)
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (hasEvents) AppleBlue else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
