package com.cuon.app.ui

import com.cuon.app.data.local.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
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

    // ===== 历史记录相关（completedAt 格式化 / 归属日期兜底 / 分组标题）=====

    @Test
    fun testFormatCompletedAt_nullReturnsEmpty() {
        assertEquals("", formatCompletedAt(null))
    }

    @Test
    fun testFormatCompletedAt_todayAndYesterday() {
        val todayAt1430 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        assertEquals("今天 14:30", formatCompletedAt(todayAt1430.timeInMillis))

        val yesterdayAt0905 = (todayAt1430.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 5)
        }
        assertEquals("昨天 09:05", formatCompletedAt(yesterdayAt0905.timeInMillis))
    }

    @Test
    fun testFormatCompletedAt_olderDates() {
        // 今年更早：按是否跨年选 pattern（1月初测试时会自然跨年）
        val tenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -10) }
        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        val targetYear = tenDaysAgo.get(Calendar.YEAR)
        val expected = if (targetYear == nowYear) {
            SimpleDateFormat("M月d日", Locale.CHINESE).format(tenDaysAgo.time)
        } else {
            SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(tenDaysAgo.time)
        }
        assertEquals(expected, formatCompletedAt(tenDaysAgo.timeInMillis))

        // 跨年（去年）：必须带年份
        val lastYear = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        val crossYearResult = formatCompletedAt(lastYear.timeInMillis)
        assertTrue("跨年格式应带年份: $crossYearResult", crossYearResult.matches(Regex("\\d{4}年\\d{1,2}月\\d{1,2}日")))
    }

    @Test
    fun testHistoryAnchorMillis_fallbackChain() {
        val fixed = 1700000000000L

        // 1. completedAt 优先
        val withAll = TaskEntity(
            title = "a", isCompleted = true, completedAt = fixed,
            startTime = fixed + 1, endTime = fixed + 2, createdAt = fixed + 3
        )
        assertEquals(fixed, historyAnchorMillis(withAll))

        // 2. 无 completedAt → startTime
        assertEquals(fixed + 1, historyAnchorMillis(withAll.copy(completedAt = null)))

        // 3. 无 completedAt/startTime → endTime
        assertEquals(fixed + 2, historyAnchorMillis(withAll.copy(completedAt = null, startTime = null)))

        // 4. 全空 → createdAt
        assertEquals(fixed + 3, historyAnchorMillis(withAll.copy(completedAt = null, startTime = null, endTime = null)))
    }

    @Test
    fun testFormatHistoryGroupLabel_todayYesterdayAndOlder() {
        val now = Calendar.getInstance()
        assertEquals("今天", formatHistoryGroupLabel(now.timeInMillis))

        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        assertEquals("昨天", formatHistoryGroupLabel(yesterday.timeInMillis))

        // 更早的日期不应再出现"今天/昨天"字样
        val older = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -15) }
        val label = formatHistoryGroupLabel(older.timeInMillis)
        assertFalse("更早日期不应是相对词: $label", label == "今天" || label == "昨天")
        assertTrue("分组标题应含日期: $label", label.contains("月"))
    }
}

