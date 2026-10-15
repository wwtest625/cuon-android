package com.cuon.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.cuon.app.data.local.AppDatabase
import com.cuon.app.data.local.TaskEntity

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    /**
     * 针对单个任务/日程排期或取消提醒。
     * 提前/准时提醒 + （有提醒的日程）结束时刻的"完成确认"通知，两条闹钟一起维护。
     */
    fun scheduleReminder(context: Context, task: TaskEntity) {
        val reminderMinutes = task.reminderMinutesBefore
        if (reminderMinutes == null || task.isCompleted) {
            cancelReminder(context, task.id)
            return
        }

        val baseTime = task.startTime ?: task.endTime ?: return
        val triggerTime = baseTime - (reminderMinutes * 60 * 1000L)

        // 若计算出的提醒时间已落在过去，则不再安排
        if (triggerTime <= System.currentTimeMillis()) {
            cancelReminder(context, task.id)
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TASK_REMINDER
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(AlarmReceiver.EXTRA_TASK_LOCATION, task.location)
            putExtra(AlarmReceiver.EXTRA_TARGET_TIME, baseTime)
            putExtra(AlarmReceiver.EXTRA_IS_CALENDAR, task.isCalendarEvent)
            putExtra(AlarmReceiver.EXTRA_MINUTES_BEFORE, reminderMinutes)
        }
        setAlarm(context, intent, task.id.toInt(), triggerTime, "提醒")

        // 有提醒的日程：额外在结束时刻排"完成确认"通知，用户 yes/no 决定是否立即归档
        if (task.isCalendarEvent && task.endTime != null && task.endTime > System.currentTimeMillis()) {
            scheduleEndConfirmAlarm(context, task)
        }
    }

    private fun scheduleEndConfirmAlarm(context: Context, task: TaskEntity) {
        val endTime = task.endTime ?: return
        if (endTime <= System.currentTimeMillis()) return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EVENT_END
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(AlarmReceiver.EXTRA_TARGET_TIME, endTime)
        }
        setAlarm(context, intent, task.id.toInt() + AlarmReceiver.END_ALARM_OFFSET, endTime, "结束确认")
    }

    /**
     * 取消特定任务的提醒（含结束确认闹钟）
     */
    fun cancelReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for ((action, code) in listOf(
            AlarmReceiver.ACTION_TASK_REMINDER to taskId.toInt(),
            AlarmReceiver.ACTION_EVENT_END to taskId.toInt() + AlarmReceiver.END_ALARM_OFFSET
        )) {
            val intent = Intent(context, AlarmReceiver::class.java).apply { this.action = action }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.i(TAG, "已取消闹钟: id=$taskId, action=$action")
            }
        }
    }

    /**
     * 重启或重新计算所有未完成的未来提醒
     */
    suspend fun rescheduleAllFutureReminders(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val activeTasks = db.taskDao().getActiveTasksWithReminder()
        val now = System.currentTimeMillis()
        var scheduledCount = 0

        for (task in activeTasks) {
            val minutes = task.reminderMinutesBefore ?: continue
            val baseTime = task.startTime ?: task.endTime ?: continue
            val triggerTime = baseTime - (minutes * 60 * 1000L)
            if (triggerTime > now) {
                scheduleReminder(context, task)
                scheduledCount++
            }
        }
        Log.i(TAG, "重排全部未来提醒完成，共激活 $scheduledCount 条")
    }

    private fun setAlarm(context: Context, intent: Intent, requestCode: Int, triggerTime: Long, label: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.i(TAG, "设置精确${label}成功: code=$requestCode, trigger=$triggerTime")
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.w(TAG, "无精确闹钟权限，降级设置低精度${label}: code=$requestCode")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.i(TAG, "设置${label}成功(API<31): code=$requestCode, trigger=$triggerTime")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "设置闹钟缺少权限: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "降级闹钟亦失败: ${fallbackEx.message}")
            }
        }
    }
}
