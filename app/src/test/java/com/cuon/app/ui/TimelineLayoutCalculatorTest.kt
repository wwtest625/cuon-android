package com.cuon.app.ui

import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.components.TimelineLayoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimelineLayoutCalculatorTest {

    private val dayStartMillis: Long

    init {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 7, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        dayStartMillis = cal.timeInMillis
    }

    private fun makeMillis(hour: Int, minute: Int): Long {
        return dayStartMillis + (hour * 60 + minute) * 60 * 1000L
    }

    @Test
    fun testSingleEvent_standardTime() {
        val event = TaskEntity(
            id = 1,
            title = "早会",
            isCalendarEvent = true,
            startTime = makeMillis(9, 0),
            endTime = makeMillis(10, 0)
        )
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(event), dayStartMillis)
        assertEquals(1, layouts.size)
        val layout = layouts.first()
        assertEquals(540, layout.startMinute) // 9 * 60 = 540
        assertEquals(600, layout.endMinute)   // 10 * 60 = 600
        assertEquals(0, layout.colIndex)
        assertEquals(1, layout.totalCols)
    }

    @Test
    fun testTwoOverlappingEvents_sideBySide() {
        // 会议 A: 10:00 - 11:30
        val eventA = TaskEntity(
            id = 1,
            title = "例会 A",
            isCalendarEvent = true,
            startTime = makeMillis(10, 0),
            endTime = makeMillis(11, 30)
        )
        // 会议 B: 10:30 - 12:00 (重叠)
        val eventB = TaskEntity(
            id = 2,
            title = "面试 B",
            isCalendarEvent = true,
            startTime = makeMillis(10, 30),
            endTime = makeMillis(12, 0)
        )

        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(eventA, eventB), dayStartMillis)
        assertEquals(2, layouts.size)
        val layoutA = layouts.first { it.task.id == 1L }
        val layoutB = layouts.first { it.task.id == 2L }

        assertEquals("两个重叠事件所在簇应总共占 2 列", 2, layoutA.totalCols)
        assertEquals("两个重叠事件所在簇应总共占 2 列", 2, layoutB.totalCols)
        assertTrue("两事件必须分配在不同列", layoutA.colIndex != layoutB.colIndex)
    }

    @Test
    fun testSequentialNonOverlappingEvents_singleColumn() {
        val event1 = TaskEntity(
            id = 1,
            title = "会议 1",
            startTime = makeMillis(9, 0),
            endTime = makeMillis(10, 0)
        )
        val event2 = TaskEntity(
            id = 2,
            title = "会议 2",
            startTime = makeMillis(10, 0),
            endTime = makeMillis(11, 0)
        )

        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(event1, event2), dayStartMillis)
        assertEquals(2, layouts.size)
        assertEquals(1, layouts[0].totalCols)
        assertEquals(0, layouts[0].colIndex)
        assertEquals(1, layouts[1].totalCols)
        assertEquals(0, layouts[1].colIndex)
    }

    @Test
    fun testMidnightAndOutOfBounds_clamped() {
        val eventCrossMidnight = TaskEntity(
            id = 1,
            title = "跨午夜加班",
            startTime = makeMillis(23, 30),
            endTime = makeMillis(25, 0)
        )
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(eventCrossMidnight), dayStartMillis)
        assertEquals(1, layouts.size)
        val layout = layouts.first()
        assertEquals(23 * 60 + 30, layout.startMinute)
        assertEquals("跨午夜结束时间应被截断在 1440 (24:00)", 1440, layout.endMinute)
    }

    @Test
    fun testZeroOrTinyDuration_minVisibleHeightEnforced() {
        val tinyEvent = TaskEntity(
            id = 1,
            title = "闪电同步",
            startTime = makeMillis(14, 0),
            endTime = makeMillis(14, 2)
        )
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(tinyEvent), dayStartMillis)
        assertEquals(1, layouts.size)
        val layout = layouts.first()
        assertTrue("微小事件需保证至少 15 分钟可视渲染高度", layout.endMinute - layout.startMinute >= 15)
    }

    @Test
    fun testFreeTimeSlotsCalculation_V2() {
        val event = TaskEntity(
            id = 1,
            title = "例会",
            startTime = makeMillis(10, 0),
            endTime = makeMillis(11, 30)
        )
        val freeSlots = TimelineLayoutCalculator.calculateFreeTimeSlots(
            listOf(event),
            dayStartMillis,
            windowStartHour = 7,
            windowEndHour = 23
        )
        assertEquals(2, freeSlots.size)
        assertEquals(7 * 60, freeSlots[0].startMinute)
        assertEquals(10 * 60, freeSlots[0].endMinute)
        assertEquals(11 * 60 + 30, freeSlots[1].startMinute)
        assertEquals(23 * 60, freeSlots[1].endMinute)
    }

    @Test
    fun testConflictDetection_V2() {
        val event1 = TaskEntity(id = 1, title = "A", startTime = makeMillis(14, 0), endTime = makeMillis(15, 0))
        val event2 = TaskEntity(id = 2, title = "B", startTime = makeMillis(14, 30), endTime = makeMillis(16, 0))
        val event3 = TaskEntity(id = 3, title = "C", startTime = makeMillis(17, 0), endTime = makeMillis(18, 0))

        val conflicts = TimelineLayoutCalculator.detectScheduleConflicts(listOf(event1, event2, event3))
        assertEquals("仅 A 与 B 存在冲突", 1, conflicts.size)
        assertEquals(1L, conflicts[0].first.id)
        assertEquals(2L, conflicts[0].second.id)
    }

    // ===== CodeBuddy 补充边缘用例 =====

    @Test
    fun testEventStartedYesterday_negativeStartClampedToZero() {
        // 跨午夜日程存储 startTime 在前一天(昨晚 22:00 开始,今天凌晨 1:00 结束)
        val yesterdayStart = dayStartMillis - 2 * 60 * 60 * 1000L // 昨天 22:00
        val event = TaskEntity(
            id = 1, title = "跨夜值班", isCalendarEvent = true,
            startTime = yesterdayStart, endTime = makeMillis(1, 0)
        )
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(event), dayStartMillis)
        assertEquals(1, layouts.size)
        val layout = layouts.first()
        assertEquals("前一天开始的日程应截断到当天 00:00", 0, layout.startMinute)
        assertEquals(60, layout.endMinute)
    }

    @Test
    fun testChainOverlappingEvents_shareColumnsCorrectly() {
        // A(9:00-10:00) 与 B(9:30-10:30) 重叠;B 与 C(10:00-11:00) 重叠;A 与 C 端点相接不重叠
        val a = TaskEntity(id = 1, title = "A", startTime = makeMillis(9, 0), endTime = makeMillis(10, 0))
        val b = TaskEntity(id = 2, title = "B", startTime = makeMillis(9, 30), endTime = makeMillis(10, 30))
        val c = TaskEntity(id = 3, title = "C", startTime = makeMillis(10, 0), endTime = makeMillis(11, 0))
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(a, b, c), dayStartMillis)

        assertEquals("链式重叠应属同一簇,最大并发 2 列", 2, layouts[0].totalCols)
        val la = layouts.first { it.task.id == 1L }
        val lb = layouts.first { it.task.id == 2L }
        val lc = layouts.first { it.task.id == 3L }
        assertTrue("A 与 B 必须异列", la.colIndex != lb.colIndex)
        assertTrue("B 与 C 必须异列", lb.colIndex != lc.colIndex)
        assertEquals("A 与 C 不重叠,可复用同列", la.colIndex, lc.colIndex)
    }

    @Test
    fun testContainedEvent_sideBySideNotStacked() {
        // 短事件完全被长事件包含:仍应左右并排,不能上下叠
        val long = TaskEntity(id = 1, title = "长会", startTime = makeMillis(13, 0), endTime = makeMillis(15, 0))
        val short = TaskEntity(id = 2, title = "插话", startTime = makeMillis(13, 30), endTime = makeMillis(14, 0))
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(long, short), dayStartMillis)
        val lLong = layouts.first { it.task.id == 1L }
        val lShort = layouts.first { it.task.id == 2L }
        assertEquals(2, lLong.totalCols)
        assertTrue("包含式重叠必须异列", lLong.colIndex != lShort.colIndex)
    }

    @Test
    fun testNullStartTime_defaultsToDayStart() {
        // 无 startTime 的日程(理论不该出现,防御):默认落到 00:00,持续 1 小时
        val event = TaskEntity(id = 1, title = "幽灵日程", isCalendarEvent = true, startTime = null, endTime = null)
        val layouts = TimelineLayoutCalculator.calculateEventLayouts(listOf(event), dayStartMillis)
        assertEquals(1, layouts.size)
        assertEquals(0, layouts[0].startMinute)
        assertEquals(60, layouts[0].endMinute)
    }

    @Test
    fun testFreeTimeSlots_minDurationFilter() {
        // 仅 15 分钟的空档(10:00-10:15)小于 30 分钟阈值,应被过滤
        val event = TaskEntity(id = 1, title = "会", startTime = makeMillis(10, 0), endTime = makeMillis(10, 15))
        val slots = TimelineLayoutCalculator.calculateFreeTimeSlots(
            listOf(event), dayStartMillis, windowStartHour = 10, windowEndHour = 11, minSlotDurationMinutes = 30
        )
        assertTrue("短于 30 分钟的空档应被过滤", slots.none { it.startMinute == 10 * 60 && it.endMinute == 10 * 60 + 15 })
    }

    @Test
    fun testGetMinuteOfDay_outOfRangeClamped() {
        // 前一天时间 → 负值应钳制为 0;超过 24:00 → 钳制为 1440
        val yesterday = dayStartMillis - 3600_000L
        assertEquals(0, TimelineLayoutCalculator.getMinuteOfDay(yesterday, dayStartMillis))
        val nextDay = dayStartMillis + 26 * 3600_000L
        assertEquals(1440, TimelineLayoutCalculator.getMinuteOfDay(nextDay, dayStartMillis))
    }

    @Test
    fun testIsSameDay_yearBoundary() {
        // 跨年相邻两天必须判为不同日(防止 DAY_OF_YEAR 相等误判)
        val d2026 = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 31, 10, 0, 0) }
        val d2027 = Calendar.getInstance().apply { set(2027, Calendar.JANUARY, 1, 10, 0, 0) }
        assertTrue("跨年两天不应判为同一天", !TimelineLayoutCalculator.isSameDay(d2026, d2027))
        val d2026b = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 31, 22, 0, 0) }
        assertTrue("同一天不同时刻应判为同一天", TimelineLayoutCalculator.isSameDay(d2026, d2026b))
    }
}
