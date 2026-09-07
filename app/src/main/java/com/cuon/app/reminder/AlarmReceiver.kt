package com.cuon.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cuon.app.MainActivity
import java.text.SimpleDateFormat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_REMINDER = "com.cuon.app.ACTION_TASK_REMINDER"
        const val CHANNEL_ID = "cuon_life_reminders"
        const val CHANNEL_NAME = "Cuon 生活事项与日程提醒"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_LOCATION = "extra_task_location"
        const val EXTRA_TARGET_TIME = "extra_target_time"
        const val EXTRA_IS_CALENDAR = "extra_is_calendar"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TASK_REMINDER) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "生活事项"
        val location = intent.getStringExtra(EXTRA_TASK_LOCATION) ?: ""
        val targetTime = intent.getLongExtra(EXTRA_TARGET_TIME, 0L)
        val isCalendar = intent.getBooleanExtra(EXTRA_IS_CALENDAR, false)
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 0)

        Log.i("AlarmReceiver", "收到事项提醒广播: taskId=$taskId, title=$title, minutesBefore=$minutesBefore")

        showNotification(
            context = context,
            taskId = taskId,
            title = title,
            location = location,
            targetTime = targetTime,
            isCalendar = isCalendar,
            minutesBefore = minutesBefore
        )
    }

    private fun showNotification(
        context: Context,
        taskId: Long,
        title: String,
        location: String,
        targetTime: Long,
        isCalendar: Boolean,
        minutesBefore: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // 注册 NotificationChannel (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "家庭、健康、生活事项及日程的准时与提前提醒"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 深链跳转 Intent：进入 MainActivity 并定位到对应日期
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SELECTED_DATE", if (targetTime > 0) targetTime else System.currentTimeMillis())
            putExtra("EXTRA_TARGET_TASK_ID", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 格式化时间与文案
        val timeText = if (targetTime > 0) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(targetTime))
        } else ""

        val notificationTitle = when {
            minutesBefore > 0 -> "【提前 ${minutesBefore} 分钟】$title"
            isCalendar -> "【日程即将开始】$title"
            else -> "【待办截止提醒】$title"
        }

        val notificationContent = buildString {
            if (timeText.isNotBlank()) append("时间: $timeText")
            if (location.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append("地点: $location")
            }
            if (isEmpty()) append("点击进入 Cuon 查看并处理事项")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(taskId.toInt(), notification)
    }
}
