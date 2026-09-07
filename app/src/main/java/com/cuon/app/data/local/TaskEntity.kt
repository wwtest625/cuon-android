package com.cuon.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办任务与日程统一实体
 * isCalendarEvent = true 时代表特定时间段的硬性日程；false 时代表任务清单
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCalendarEvent: Boolean = false,
    val startTime: Long? = null,        // 毫秒时间戳
    val endTime: Long? = null,          // 毫秒时间戳
    val isCompleted: Boolean = false,
    val priority: String = "medium",    // high, medium, low
    val tag: String = "默认",
    val location: String = "",
    val reminderMinutesBefore: Int? = null, // null=不提醒, 0=准时, 15=提前15分钟, 30=提前30分钟
    val createdAt: Long = System.currentTimeMillis()
)
