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
import com.cuon.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_REMINDER = "com.cuon.app.ACTION_TASK_REMINDER"
        const val ACTION_MORNING_DIGEST = MorningDigestScheduler.ACTION_MORNING_DIGEST
        const val CHANNEL_ID = "cuon_life_reminders"
        const val CHANNEL_NAME = "Cuon 生活事项与日程提醒"
        const val CHANNEL_DIGEST_ID = "cuon_morning_digest"
        const val CHANNEL_DIGEST_NAME = "Cuon 每日晨报"

        /** 晨报通知固定 ID，高位取值避免与 taskId 冲突 */
        private const val DIGEST_NOTIFICATION_ID = 19000009

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_LOCATION = "extra_task_location"
        const val EXTRA_TARGET_TIME = "extra_target_time"
        const val EXTRA_IS_CALENDAR = "extra_is_calendar"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MORNING_DIGEST -> handleMorningDigest(context)
            ACTION_TASK_REMINDER -> handleTaskReminder(context, intent)
        }
    }

    /**
     * 每日晨报：查当天/最近事项，生成俏皮早安通知，并排定次日闹钟
     */
    private fun handleMorningDigest(context: Context) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                showMorningDigest(appContext)
                MorningDigestScheduler.scheduleDailyDigest(appContext)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "晨报生成失败: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun showMorningDigest(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DIGEST_ID,
                CHANNEL_DIGEST_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天早九点的日程与待办早安摸底，轻量不打扰"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val (title, content) = buildDigestCopy(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SELECTED_DATE", System.currentTimeMillis())
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DIGEST_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST_ID)
            .setSmallIcon(com.cuon.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(DIGEST_NOTIFICATION_ID, notification)
    }

    private suspend fun buildDigestCopy(context: Context): Pair<String, String> {
        val pendingTasks = AppDatabase.getDatabase(context).taskDao().getPendingTasks()

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L

        fun anchor(t: com.cuon.app.data.local.TaskEntity): Long? = t.startTime ?: t.endTime

        val todayTasks = pendingTasks
            .filter { val t = anchor(it); t != null && t >= dayStart && t < dayEnd }
            .sortedBy { anchor(it) ?: Long.MAX_VALUE }
        val undatedTasks = pendingTasks.filter { anchor(it) == null }
        val nearestUpcoming = pendingTasks
            .filter { val t = anchor(it); t != null && t >= dayEnd }
            .minByOrNull { anchor(it) ?: Long.MAX_VALUE }

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("M月d日", Locale.getDefault())

        // 无时间待办也纳入「今天没安排」分支的提示，避免纯文字待办被晨报无视
        return when {
            todayTasks.isNotEmpty() -> {
                val nearest = todayTasks.first()
                val t = anchor(nearest)!!
                val nearestText = if (nearest.startTime != null) {
                    "${timeFmt.format(Date(t))} 的「${nearest.title}」"
                } else {
                    "「${nearest.title}」"
                }
                val countText = if (todayTasks.size == 1) "1 件事" else "${todayTasks.size} 件事"
                val extra = if (undatedTasks.isNotEmpty()) {
                    "（还有 ${undatedTasks.size} 件无日期小事）"
                } else ""
                "🌅 早安！今天有 $countText 等你翻牌" to
                    "最近的是 $nearestText$extra，先吃个早饭，别焦虑～"
            }
            nearestUpcoming != null -> {
                val t = anchor(nearestUpcoming)!!
                val tail = if (undatedTasks.isNotEmpty()) {
                    "。还有 ${undatedTasks.size} 件小事没办完，顺手清掉吧 ☀️"
                } else "，享受慢生活吧 ☀️"
                "🌅 早安！今天日程表干净" to
                    "最近的事是 ${dateFmt.format(Date(t))} 的「${nearestUpcoming.title}」$tail"
            }
            undatedTasks.isNotEmpty() ->
                "🌅 早安！今天没有定时安排" to
                    "不过还有 ${undatedTasks.size} 件小事等你顺手办掉，先吃个早饭吧 ☀️"
            else -> "🌅 早安！" to "近期没有待办日程，今天自由安排，好好生活呀 ☀️"
        }
    }

    private fun handleTaskReminder(context: Context, intent: Intent) {
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
            .setSmallIcon(com.cuon.app.R.drawable.ic_notification)
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
