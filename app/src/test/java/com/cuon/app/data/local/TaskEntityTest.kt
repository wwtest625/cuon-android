package com.cuon.app.data.local

import org.junit.Assert.*
import org.junit.Test

class TaskEntityTest {

    @Test
    fun testTaskEntity_defaultValuesAndCopy() {
        val task = TaskEntity(
            title = "完成 PRD 初稿",
            priority = "high",
            tag = "工作"
        )

        assertEquals("完成 PRD 初稿", task.title)
        assertFalse("默认未完成", task.isCompleted)
        assertFalse("默认非日程", task.isCalendarEvent)
        assertEquals("high", task.priority)
        assertEquals("工作", task.tag)
        assertTrue("自动生成创建时间", task.createdAt > 0)

        // 模拟打勾完成
        val completedTask = task.copy(isCompleted = true)
        assertTrue("打勾后应标记为已完成", completedTask.isCompleted)
        assertEquals(task.title, completedTask.title)
    }
}
