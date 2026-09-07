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
}
