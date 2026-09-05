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
}
