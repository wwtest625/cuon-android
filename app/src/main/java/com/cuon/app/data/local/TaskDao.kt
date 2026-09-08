package com.cuon.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, startTime ASC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCalendarEvent = 1 ORDER BY startTime ASC")
    fun getCalendarEventsFlow(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND reminderMinutesBefore IS NOT NULL")
    suspend fun getActiveTasksWithReminder(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    /** 一次性取全部未完成任务（晨报、AI 工具查询用） */
    @Query("SELECT * FROM tasks WHERE isCompleted = 0")
    suspend fun getPendingTasks(): List<TaskEntity>

    /** 一次性取全部任务（AI 工具查询已完成事项用） */
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    /** 重复任务防重：同标题同规则下，指定周期锚点的未完成实例是否已存在 */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE title = :title AND recurrenceRule = :rule " +
            "AND (startTime = :anchor OR endTime = :anchor) AND isCompleted = 0"
    )
    suspend fun countPendingRecurrenceAt(title: String, rule: String, anchor: Long): Int

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}
