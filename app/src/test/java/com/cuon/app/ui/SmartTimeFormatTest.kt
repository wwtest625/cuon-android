package com.cuon.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.*

class SmartTimeFormatTest {

    @Test
    fun testGetRelativeBadgeText_todayTomorrowFuture() {
        val now = Calendar.getInstance()

        // 今天
        val todayMillis = now.timeInMillis
        assertEquals("今天", getRelativeBadgeText(todayMillis))

        // 明天
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        assertEquals("明天", getRelativeBadgeText(tomorrow.timeInMillis))

        // 后天
        val dayAfterTomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }
        assertEquals("后天", getRelativeBadgeText(dayAfterTomorrow.timeInMillis))

        // 4天后
        val fourDaysLater = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 4) }
        assertEquals("4天后", getRelativeBadgeText(fourDaysLater.timeInMillis))

        // null 保护
        assertEquals("", getRelativeBadgeText(null))
    }

    @Test
    fun testFormatSmartDateTime_nullStartTime() {
        val result = formatSmartDateTime(null, null)
        assertEquals("今日待安排", result)
    }

    @Test
    fun testFormatSmartDeadline_pastDeadline() {
        val pastTime = System.currentTimeMillis() - 100_000
        val result = formatSmartDeadline(pastTime)
        assertEquals("已逾期", result)
    }

    @Test
    fun testGetDaysDiffFromToday_yearBoundary() {
        // 验证跨年自然日毫秒差算法
        val endOfYear = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 31, 23, 59, 0)
        }
        val startOfNextYear = Calendar.getInstance().apply {
            set(2027, Calendar.JANUARY, 1, 0, 1, 0)
        }
        val endOfYearZero = (endOfYear.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfNextYearZero = (startOfNextYear.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = (startOfNextYearZero.timeInMillis - endOfYearZero.timeInMillis) / (24 * 3600 * 1000L)
        assertEquals("跨年12月31日到次年1月1日相隔应恰好为1天", 1L, diffDays)
    }
}

