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

    @Test
    fun testCompletedAt_defaultNullAndCopyBehavior() {
        val task = TaskEntity(title = "倒垃圾")

        assertEquals("新任务默认无完成时间", null, task.completedAt)

        // 打勾记录完成时间
        val doneAt = 1700000000000L
        val completed = task.copy(isCompleted = true, completedAt = doneAt)
        assertEquals(doneAt, completed.completedAt)
        assertTrue(completed.isCompleted)

        // 取消完成应清空时间戳
        val uncompleted = completed.copy(isCompleted = false, completedAt = null)
        assertFalse(uncompleted.isCompleted)
        assertEquals(null, uncompleted.completedAt)

        // copy 保留语义：重复任务 advanceRecurrence 用 copy(id=0, isCompleted=false)
        // 生成下一周期实例时必须显式 completedAt = null，否则会带上旧实例的完成时间
        val nextOccurrence = completed.copy(id = 0, isCompleted = false, completedAt = null)
        assertEquals("下一周期实例不应带完成时间", null, nextOccurrence.completedAt)
        assertEquals("下一周期实例不应已完成", false, nextOccurrence.isCompleted)
    }

    @Test
    fun testDeletedAt_softDeleteBehavior() {
        val task = TaskEntity(title = "买菜")

        assertEquals("新任务默认不在回收站", null, task.deletedAt)

        // 软删除：只打时间戳，isCompleted 等内容字段原样保留（恢复时不丢状态）
        val deletedAt = 1700000000000L
        val trashed = task.copy(deletedAt = deletedAt)
        assertEquals(deletedAt, trashed.deletedAt)
        assertFalse(trashed.isCompleted)

        // 恢复 = deletedAt 置回 null，内容不丢
        val restored = trashed.copy(deletedAt = null)
        assertEquals(null, restored.deletedAt)
        assertEquals(task.title, restored.title)
    }
}
