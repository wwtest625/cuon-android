package com.cuon.app.util

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.cuon.app.data.local.TaskEntity
import java.util.*

object CalendarSyncHelper {

    /**
     * 将日程实体同步写入 Android 系统原生日历 (CalendarContract)
     * 写入成功返回系统 Event ID，权限未授予或失败返回 null
     */
    fun insertEventToSystemCalendar(context: Context, task: TaskEntity): Long? {
        if (!task.isCalendarEvent || task.startTime == null) return null

        return try {
            val endTime = task.endTime ?: (task.startTime + 3600_000) // 默认持续1小时
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, task.startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.TITLE, task.title)
                put(CalendarContract.Events.DESCRIPTION, "由 Cuon AI 个人工作台智能同步")
                put(CalendarContract.Events.EVENT_LOCATION, task.location)
                put(CalendarContract.Events.CALENDAR_ID, 1) // 默认系统日历账户
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            // 权限未获得时静默保护
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
