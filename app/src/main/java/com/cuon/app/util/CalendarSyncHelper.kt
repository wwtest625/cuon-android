package com.cuon.app.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.cuon.app.data.local.TaskEntity
import java.util.TimeZone

/**
 * 单次插入尝试的结果分类（仅供重试循环判定，不涉及 Android 类型，便于单测）
 */
internal sealed interface InsertAttemptResult {
    /** 插入成功并拿到系统 Event ID */
    data class Inserted(val eventId: Long) : InsertAttemptResult

    /** insert 返回了空 URI：calendarId 可能已失效，值得清缓存后重试 */
    data object EmptyResult : InsertAttemptResult

    /** insert 抛出异常：权限被撤销、日历被删除等都归入此类 */
    data object Threw : InsertAttemptResult
}

/**
 * 将 Cuon 的日程实体同步写入 Android 系统原生日历 (CalendarContract)
 *
 * 设计要点：
 * - **不硬编码 CALENDAR_ID**：不同设备/账户的日历 ID 并不固定，写死 1 在多数机型上直接抛错或写进错误账户。
 *   改为运行时查询 `CalendarContract.Calendars`，按「主日历 > 可写 > 可见」择优。
 * - **只缓存正结果**：查询为空（用户尚未添加/启用日历）或查询异常都**不写入缓存**，
 *   这样用户后续新增日历时能立即被发现，不会整个进程生命周期都返回 null。
 * - **任何未产出 eventId 的情况都会重试**：空 URI 与抛异常一律清缓存并重新解析，
 *   只要还有剩余次数就再试一轮，避免陈旧 calendarId 让同步静默失败。
 * - **权限前置**：先校验 READ_CALENDAR / WRITE_CALENDAR，缺失则静默跳过（不崩溃、不弹窗）。
 *
 * 返回系统 Event ID；任何失败均返回 null，由调用方决定是否提示。
 */
object CalendarSyncHelper {

    private const val TAG = "CalendarSyncHelper"

    /** 单次同步的最大尝试次数：首次失败清缓存后允许再解析一次 */
    private const val MAX_INSERT_ATTEMPTS = 2

    /**
     * 日历账户选择缓存。
     * 日历列表在进程生命周期内极少变动，缓存可避免每次同步都做一次 ContentResolver 查询。
     * 注意：[cacheResolved] 仅在解析到有效 ID 时才为 true，负结果不缓存。
     */
    @Volatile
    private var cachedCalendarId: Long? = null
    private var cacheResolved = false

    /**
     * 主动失效日历 ID 缓存。
     * 查询结果为空、查询异常或写入未产出事件时都应调用，强制下次重新解析。
     */
    fun invalidateCalendarIdCache() {
        cacheResolved = false
        cachedCalendarId = null
    }

    /**
     * 将日程实体同步写入系统原生日历；权限未授予或失败返回 null
     */
    fun insertEventToSystemCalendar(context: Context, task: TaskEntity): Long? {
        if (!task.isCalendarEvent || task.startTime == null) return null

        return insertEventWithRetry(
            resolveCalendarId = {
                // 权限缺失视为「无可解析日历」，直接终止（不重试）
                if (hasCalendarPermission(context)) resolveWritableCalendarId(context) else null
            },
            insertEvent = { calendarId -> doInsert(context, task, calendarId) },
            onInvalidateCache = { invalidateCalendarIdCache() },
            onAttemptFailed = { attempt, result ->
                val reason = when (result) {
                    is InsertAttemptResult.EmptyResult -> "插入返回空 URI"
                    is InsertAttemptResult.Threw -> "插入抛异常"
                    is InsertAttemptResult.Inserted -> ""
                }
                if (attempt == MAX_INSERT_ATTEMPTS) {
                    Log.w(TAG, "$reason，已达最大重试次数，放弃系统日历同步: ${task.title}")
                } else {
                    Log.w(TAG, "$reason，清缓存后重试: ${task.title}")
                }
            },
            onInserted = { calendarId, eventId ->
                Log.i(TAG, "已同步到系统日历: calendarId=$calendarId, eventId=$eventId")
            }
        )
    }

    /**
     * 带重试的插入循环（纯逻辑，不含 Android 类型，可单测）。
     *
     * 语义：**只有拿到 eventId、或已用尽次数、或确认无可用日历时才返回**。
     * 「空 URI」与「抛异常」都视为本次尝试未产出事件 —— 清缓存后只要还有次数就重新解析再试，
     * 绝不因为第一次拿到空 URI 就提前结束。
     *
     * @param resolveCalendarId 解析可写日历 ID；返回 null 表示无可用日历，直接放弃（重试无意义）
     * @param insertEvent 用给定 calendarId 执行插入，返回 eventId 或 null（空 URI）
     * @param onInvalidateCache 每次尝试失败后调用，用于清缓存使下次重新解析
     */
    internal fun insertEventWithRetry(
        maxAttempts: Int = MAX_INSERT_ATTEMPTS,
        resolveCalendarId: () -> Long?,
        insertEvent: (Long) -> Long?,
        onInvalidateCache: () -> Unit,
        onAttemptFailed: (attempt: Int, result: InsertAttemptResult) -> Unit = { _, _ -> },
        onInserted: (calendarId: Long, eventId: Long) -> Unit = { _, _ -> }
    ): Long? {
        for (attempt in 1..maxAttempts) {
            val calendarId = resolveCalendarId()
            if (calendarId == null) {
                // 无可用日历（含权限缺失）：重试不会改变结果
                return null
            }

            val result = try {
                val eventId = insertEvent(calendarId)
                if (eventId == null) {
                    InsertAttemptResult.EmptyResult
                } else {
                    InsertAttemptResult.Inserted(eventId)
                }
            } catch (e: Exception) {
                InsertAttemptResult.Threw
            }

            // 成功：立即返回
            if (result is InsertAttemptResult.Inserted) {
                onInserted(calendarId, result.eventId)
                return result.eventId
            }

            // 未产出事件：清缓存，让下一次迭代重新解析日历
            onInvalidateCache()
            onAttemptFailed(attempt, result)

            // 已用尽次数：收工返回 null（此处绝不能 fallthrough 后再 return null 前多做一次插入）
            if (attempt == maxAttempts) return null
        }

        return null
    }

    /** 执行一次真实的 ContentResolver 插入，返回系统 Event ID 或 null */
    private fun doInsert(context: Context, task: TaskEntity, calendarId: Long): Long? {
        val startTime = task.startTime ?: return null
        val endTime = task.endTime ?: (startTime + 3600_000) // 默认持续1小时
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, task.title)
            put(CalendarContract.Events.DESCRIPTION, "由 Cuon AI 个人工作台智能同步")
            put(CalendarContract.Events.EVENT_LOCATION, task.location)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    /** 是否已同时授予日历读与写权限 */
    private fun hasCalendarPermission(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 解析一个可用于写入事件的日历 ID。
     *
     * 排序优先级：
     * 1. IS_PRIMARY 主日历优先（用户最可能查看的账户）
     * 2. 其次要求具备 CONTRIBUTOR 及以上访问级别（EDITOR/OWNER/CONTRIBUTOR 才可写）
     * 3. 最后要求日历可见（VISIBLE=1），避免写进被隐藏的订阅日历
     */
    private fun resolveWritableCalendarId(context: Context): Long? {
        if (cacheResolved) return cachedCalendarId

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        // 只取有写权限的日历；可见性在内存中排序时再考虑，避免 SQL 层过度筛选
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val resolved = try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                var visibleFallback: Long? = null
                var anyMatch: Long? = null

                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val primaryIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
                val visibleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val isPrimary = cursor.getInt(primaryIndex) == 1
                    val isVisible = cursor.getInt(visibleIndex) == 1

                    // 最高优先级命中：主日历，直接采用
                    if (isPrimary && isVisible) return@use id

                    if (isPrimary && anyMatch == null) anyMatch = id
                    if (isVisible && visibleFallback == null) visibleFallback = id
                    if (anyMatch == null) anyMatch = id
                }

                visibleFallback ?: anyMatch
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询系统日历列表失败: ${e.message}", e)
            null
        }

        if (resolved == null) {
            // 负结果不缓存：用户可能稍后新增/启用日历，下次调用必须重新查询
            cacheResolved = false
            cachedCalendarId = null
            Log.i(TAG, "未解析到可写日历（不缓存负结果，下次将重新查询）")
            return null
        }

        cachedCalendarId = resolved
        cacheResolved = true
        return resolved
    }
}
