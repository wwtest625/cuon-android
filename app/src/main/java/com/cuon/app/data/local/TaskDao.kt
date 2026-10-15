package com.cuon.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY isCompleted ASC, startTime ASC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    /** 回收站：软删除事项按删除时间倒序 */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashFlow(): Flow<List<TaskEntity>>

    /** 自动归档候选：未完成的日程，结束（或开始）时间早于上限 */
    @Query(
        "SELECT * FROM tasks WHERE isCalendarEvent = 1 AND isCompleted = 0 AND deletedAt IS NULL " +
            "AND COALESCE(endTime, startTime) IS NOT NULL AND COALESCE(endTime, startTime) < :upper " +
            "ORDER BY COALESCE(endTime, startTime) ASC"
    )
    suspend fun getUnarchivedFinishedCalendarEventsBefore(upper: Long): List<TaskEntity>

    /** 备份导出：含回收站在内物理全量 */
    @Query("SELECT * FROM tasks")
    suspend fun getAllIncludingTrashOnce(): List<TaskEntity>

    /** 备份覆盖导入：清空全表 */
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    /** 合并导入防重：同标题同开始时间同完成态视为重复 */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE title = :title " +
            "AND IFNULL(startTime, -1) = IFNULL(:startTime, -1) AND isCompleted = :isCompleted"
    )
    suspend fun countDuplicate(title: String, startTime: Long?, isCompleted: Boolean): Int

    @Query("SELECT * FROM tasks WHERE isCalendarEvent = 1 AND deletedAt IS NULL ORDER BY startTime ASC")
    fun getCalendarEventsFlow(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND reminderMinutesBefore IS NOT NULL AND deletedAt IS NULL")
    suspend fun getActiveTasksWithReminder(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    /** 一次性取全部未完成任务（晨报、AI 工具查询用） */
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND deletedAt IS NULL")
    suspend fun getPendingTasks(): List<TaskEntity>

    /** 一次性取全部任务（AI 工具查询已完成事项用） */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    /** 重复任务防重：同标题同规则下，指定周期锚点的未完成实例是否已存在 */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE title = :title AND recurrenceRule = :rule " +
            "AND (startTime = :anchor OR endTime = :anchor) AND isCompleted = 0 AND deletedAt IS NULL"
    )
    suspend fun countPendingRecurrenceAt(title: String, rule: String, anchor: Long): Int

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    /** 软删除：打删除时间戳进回收站 */
    @Query("UPDATE tasks SET deletedAt = :now WHERE id = :id")
    suspend fun softDeleteTask(id: Long, now: Long)

    /** 从回收站恢复 */
    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreTask(id: Long)

    /** 彻底删除回收站中超过保留期的事项，返回清理条数 */
    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeExpiredTrash(before: Long): Int
}
