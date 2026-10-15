package com.cuon.app.reminder

import com.cuon.app.data.local.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * 日程自动归档规则边界测试（纯函数，无 DB）：
 * - 无提醒日程：次日晨报归档（end < 今日 0 点）
 * - 有提醒日程：第三天晨报兜底（end < 昨日 0 点）
 */
class CalendarAutoArchiverTest {

    private val day = CalendarAutoArchiver.DAY_MILLIS

    /** 以"今天中午 12:00"为 now 基准，构造相对天数 + 小时的时间点 */
    private fun at(dayOffset: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis

    private val now = at(0, 12) // 今日 12:00，模拟晨报时刻

    private fun event(endOffset: Int, reminder: Int? = null): TaskEntity =
        TaskEntity(
            title = "看牙",
            isCalendarEvent = true,
            startTime = at(endOffset, 9),
            endTime = at(endOffset, 10),
            reminderMinutesBefore = reminder
        )

    @Test
    fun `无提醒日程 昨天结束 今日晨报归档`() {
        assertTrue(CalendarAutoArchiver.shouldArchive(event(-1, reminder = null), now))
    }

    @Test
    fun `无提醒日程 今天结束 不归档`() {
        // 今天 09:00-10:00 的日程，中午 12 点跑扫描时 end > 今日0点，等明早
        assertFalse(CalendarAutoArchiver.shouldArchive(event(0, reminder = null), now))
    }

    @Test
    fun `有提醒日程 昨天结束仍在确认窗口 不归档`() {
        assertFalse(CalendarAutoArchiver.shouldArchive(event(-1, reminder = 15), now))
    }

    @Test
    fun `有提醒日程 前天结束 第三天晨报兜底归档`() {
        assertTrue(CalendarAutoArchiver.shouldArchive(event(-2, reminder = 15), now))
    }

    @Test
    fun `已归档与回收站日程不重复归档`() {
        val done = event(-5, reminder = null).copy(isCompleted = true, completedAt = at(-5, 10))
        assertFalse("已完成不再归档", CalendarAutoArchiver.shouldArchive(done, now))

        val trashed = event(-5, reminder = null).copy(deletedAt = at(-1, 8))
        assertFalse("回收站事项不参与归档", CalendarAutoArchiver.shouldArchive(trashed, now))
    }

    @Test
    fun `待办不参与日程归档 无时间锚点跳过`() {
        val todo = TaskEntity(title = "买菜", endTime = at(-3, 18))
        assertFalse("isCalendarEvent=false 不归档", CalendarAutoArchiver.shouldArchive(todo, now))

        val timeless = TaskEntity(title = "随到随办", isCalendarEvent = true)
        assertFalse("无 startTime/endTime 无法归属", CalendarAutoArchiver.shouldArchive(timeless, now))
    }

    @Test
    fun `归档锚点 结束时间优先`() {
        val withEnd = TaskEntity(title = "会议", isCalendarEvent = true, startTime = 1000L, endTime = 2000L)
        assertEquals(2000L, CalendarAutoArchiver.archiveAnchor(withEnd))

        val startOnly = TaskEntity(title = "全天事件", isCalendarEvent = true, startTime = 1000L)
        assertEquals(1000L, CalendarAutoArchiver.archiveAnchor(startOnly))
    }

    @Test
    fun `归档时间归属结束日而非归档时刻`() {
        // 昨天 10:00 结束的无提醒日程，今天中午归档：completedAt 应为昨天 10:00
        val task = event(-1, reminder = null)
        val expected = at(-1, 10)
        assertTrue("前置条件：昨天日程今天应可归档", CalendarAutoArchiver.shouldArchive(task, now))
        assertEquals(expected, CalendarAutoArchiver.archiveAnchor(task))
    }
}
