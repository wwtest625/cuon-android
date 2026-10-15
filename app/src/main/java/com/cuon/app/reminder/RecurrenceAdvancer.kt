package com.cuon.app.reminder

import com.cuon.app.data.local.TaskDao
import com.cuon.app.data.local.TaskEntity

/**
 * 重复任务完成后的下一周期推进（ViewModel 手动勾选与日程自动归档共用）。
 * 生成下一周期实例后立即排提醒；同标题同规则同锚点已有未完成实例时跳过防重。
 */
object RecurrenceAdvancer {

    /** @return 新实例 id，非重复任务 / 已存在下一实例 / 无时间锚点时返回 null */
    suspend fun advance(taskDao: TaskDao, task: TaskEntity): Long? {
        val rule = task.recurrenceRule ?: return null
        if (!RecurrenceHelper.isRecurrence(task)) return null
        val anchor = task.startTime ?: task.endTime ?: return null

        val nextAnchor = RecurrenceHelper.nextOccurrence(rule, anchor)
        if (taskDao.countPendingRecurrenceAt(task.title, rule, nextAnchor) > 0) return null

        val shiftMillis = nextAnchor - anchor
        fun shift(t: Long?): Long? = t?.let { it + shiftMillis }
        val next = task.copy(
            id = 0,
            isCompleted = false,
            completedAt = null,
            startTime = shift(task.startTime),
            endTime = shift(task.endTime),
            createdAt = System.currentTimeMillis()
        )
        return taskDao.insertTask(next)
    }
}
