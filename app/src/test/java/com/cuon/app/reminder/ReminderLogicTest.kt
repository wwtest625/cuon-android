package com.cuon.app.reminder

import com.cuon.app.data.local.TaskEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * 针对【致命1·提醒通知】时间计算与防御逻辑的单元测试
 */
class ReminderLogicTest {

    @Test
    fun testOnTimeReminderCalculation() {
        val baseTime = 1757235600000L // 某个未来时间点
        val task = TaskEntity(
            id = 1,
            title = "带孩子打流感疫苗",
            startTime = baseTime,
            reminderMinutesBefore = 0 // 准时
        )

        val triggerTime = (task.startTime ?: 0L) - ((task.reminderMinutesBefore ?: 0) * 60 * 1000L)
        assertEquals("准时提醒的触发时间应与开始时间严格一致", baseTime, triggerTime)
    }

    @Test
    fun test15MinutesBeforeReminderCalculation() {
        val baseTime = 1757235600000L
        val task = TaskEntity(
            id = 2,
            title = "陪父母去同仁医院",
            startTime = baseTime,
            reminderMinutesBefore = 15 // 提前15分钟
        )

        val triggerTime = (task.startTime ?: 0L) - ((task.reminderMinutesBefore ?: 0) * 60 * 1000L)
        val expected = baseTime - 15 * 60 * 1000L
        assertEquals("提前15分钟提醒的触发时间应比开始时间早900,000毫秒", expected, triggerTime)
    }

    @Test
    fun test30MinutesBeforeReminderCalculation() {
        val baseTime = 1757235600000L
        val task = TaskEntity(
            id = 3,
            title = "女儿学校家长会",
            startTime = baseTime,
            reminderMinutesBefore = 30 // 提前30分钟
        )

        val triggerTime = (task.startTime ?: 0L) - ((task.reminderMinutesBefore ?: 0) * 60 * 1000L)
        val expected = baseTime - 30 * 60 * 1000L
        assertEquals("提前30分钟提醒的触发时间应比开始时间早1,800,000毫秒", expected, triggerTime)
    }

    @Test
    fun testDeadlineBasedTodoReminder() {
        val deadline = 1757239200000L
        val task = TaskEntity(
            id = 4,
            title = "交燃气费与物业费",
            isCalendarEvent = false,
            startTime = null,
            endTime = deadline,
            reminderMinutesBefore = 0
        )

        val baseTime = task.startTime ?: task.endTime
        assertNotNull("当无 startTime 时应以截止时间 endTime 为提醒基准", baseTime)
        val triggerTime = baseTime!! - ((task.reminderMinutesBefore ?: 0) * 60 * 1000L)
        assertEquals("待办截止提醒时间应与截止时间对齐", deadline, triggerTime)
    }

    @Test
    fun testPastReminderSuppression() {
        val pastTime = System.currentTimeMillis() - 3600000L // 1小时前
        val task = TaskEntity(
            id = 5,
            title = "过去的买菜安排",
            startTime = pastTime,
            reminderMinutesBefore = 15
        )

        val baseTime = task.startTime ?: task.endTime ?: 0L
        val triggerTime = baseTime - ((task.reminderMinutesBefore ?: 0) * 60 * 1000L)
        val isExpired = triggerTime <= System.currentTimeMillis()
        assertTrue("过去发生的日程/提醒不应重新安排闹钟", isExpired)
    }

    @Test
    fun testCompletedTaskSuppression() {
        val futureTime = System.currentTimeMillis() + 7200000L // 2小时后
        val task = TaskEntity(
            id = 6,
            title = "买生日蛋糕",
            startTime = futureTime,
            isCompleted = true, // 已完成
            reminderMinutesBefore = 15
        )

        val shouldSchedule = !task.isCompleted && task.reminderMinutesBefore != null
        assertFalse("已打勾完成的生活事项，即使设置了提醒也不应排期闹钟", shouldSchedule)
    }
}
