package com.cuon.app.reminder

import android.content.Context
import android.util.Log
import com.cuon.app.data.local.AppDatabase
import com.cuon.app.data.local.TaskDao
import com.cuon.app.data.local.TaskEntity
import java.util.Calendar

/**
 * 日程自动归档（留存）：
 *
 * - 无提醒日程：结束次日晨报即归档（end < 今日 0 点）
 * - 有提醒日程：endTime 到点会弹"已完成/稍后"确认通知；若用户一直未确认，
 *   第三天晨报兜底归档（end < 昨日 0 点，即结束日+2 的早晨）
 *
 * 归档动作：isCompleted=true，completedAt=事件原结束时间（历史归属到结束那天，
 * 而非归档执行时刻）；重复日程同步推进下一周期实例。
 */
object CalendarAutoArchiver {
    private const val TAG = "CalendarAutoArchiver"

    const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** 归档归属时间锚点：结束时间优先，无结束时间用开始时间 */
    fun archiveAnchor(task: TaskEntity): Long? = task.endTime ?: task.startTime

    /**
     * 判定某日程此刻是否应被归档。
     *
     * @param now 当前毫秒时间戳（今日所在时刻）
     */
    fun shouldArchive(task: TaskEntity, now: Long): Boolean {
        if (!task.isCalendarEvent || task.isCompleted || task.deletedAt != null) return false
        val end = archiveAnchor(task) ?: return false
        val todayStart = dayStartMillis(now)
        return if (task.reminderMinutesBefore != null) {
            // 有提醒：给足确认窗口，结束日 +2 的早晨（第三天晨报）兜底
            end < todayStart - DAY_MILLIS
        } else {
            // 无提醒：次日晨报即归档
            end < todayStart
        }
    }

    /** 扫描并归档全部到期日程，返回归档条数（启动期与每日晨报共用） */
    suspend fun scanAndArchive(context: Context): Int {
        val taskDao = AppDatabase.getDatabase(context).taskDao()
        val now = System.currentTimeMillis()
        // 先取"最宽松"的边界（今日 0 点），提醒分支的更严格条件在内存里判定
        val candidates = taskDao.getUnarchivedFinishedCalendarEventsBefore(now)
        var archived = 0
        for (task in candidates) {
            if (!shouldArchive(task, now)) continue
            archive(context, taskDao, task, now)
            archived++
        }
        if (archived > 0) Log.i(TAG, "日程自动归档: $archived 条")
        return archived
    }

    /** 归档单条日程（通知"已完成"按钮与晨报扫描共用） */
    suspend fun archive(context: Context, taskDao: TaskDao, task: TaskEntity, now: Long) {
        val completed = task.copy(
            isCompleted = true,
            completedAt = archiveAnchor(task) ?: now
        )
        taskDao.updateTask(completed)
        ReminderScheduler.cancelReminder(context, task.id)
        val newId = RecurrenceAdvancer.advance(taskDao, task)
        if (newId != null) {
            ReminderScheduler.scheduleReminder(context, completed.copy(id = newId, isCompleted = false))
        }
    }

    fun dayStartMillis(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
