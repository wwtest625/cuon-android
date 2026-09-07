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
     * 针对单个任务/日程排期或取消提醒
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

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TASK_REMINDER
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(AlarmReceiver.EXTRA_TASK_LOCATION, task.location)
            putExtra(AlarmReceiver.EXTRA_TARGET_TIME, baseTime)
            putExtra(AlarmReceiver.EXTRA_IS_CALENDAR, task.isCalendarEvent)
            putExtra(AlarmReceiver.EXTRA_MINUTES_BEFORE, reminderMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.i(TAG, "设置精确提醒成功: id=${task.id}, trigger=$triggerTime, minutesBefore=$reminderMinutes")
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.w(TAG, "无精确闹钟权限，降级设置低精度提醒: id=${task.id}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.i(TAG, "设置提醒成功(API<31): id=${task.id}, trigger=$triggerTime")
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

    /**
     * 取消特定任务的提醒
     */
    fun cancelReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TASK_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "已取消提醒: id=$taskId")
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
}
