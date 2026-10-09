package com.cuon.app.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 智能相对时间格式化工具。
 *
 * 从 HomeScreen 拆出（纯函数、无 Compose 依赖），
 * 便于单元测试与复用：SmartTimeFormatTest 依赖本包路径。
 */

/** 目标时间与「今天 00:00」相差的自然日天数（按本地时区归零后求差） */
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

/** 日程卡片右侧相对时间徽标：今天 / 明天 / 后天 / N天后 / 未来 / 昨天 / 已过去 */
fun getRelativeBadgeText(startTime: Long?): String {
    if (startTime == null) return ""
    return when (val diffDays = getDaysDiffFromToday(startTime)) {
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        in 3L..7L -> "${diffDays}天后"
        -1L -> "昨天"
        in Long.MIN_VALUE until -1L -> "已过去"
        else -> "未来"
    }
}

/** 日程卡片时间行：今天/明天/后天 HH:mm - HH:mm，其余显示绝对日期 */
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

/** 待办截止时间：已逾期 / 今天 HH:mm 前 / 明天… / 绝对日期 */
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
