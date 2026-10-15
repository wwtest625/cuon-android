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
        private const val TAG = "AlarmReceiver"

        const val ACTION_TASK_REMINDER = "com.cuon.app.ACTION_TASK_REMINDER"
        const val ACTION_MORNING_DIGEST = MorningDigestScheduler.ACTION_MORNING_DIGEST

        /** 日程结束时刻：弹"已完成/稍后"确认通知 */
        const val ACTION_EVENT_END = "com.cuon.app.ACTION_EVENT_END"

        /** 用户在结束确认通知上点"已完成" → 立即归档 */
        const val ACTION_ARCHIVE_CONFIRM = "com.cuon.app.ACTION_ARCHIVE_CONFIRM"

        /** 用户点"稍后" → 仅关闭通知，第三天晨报兜底归档 */
        const val ACTION_EVENT_END_DISMISS = "com.cuon.app.ACTION_EVENT_END_DISMISS"

        /** 结束确认相关的闹钟/通知/按钮 Intent 统一偏移，避免与提前提醒（requestCode=taskId）冲突 */
        const val END_ALARM_OFFSET = 1_000_000_000
        const val ARCHIVE_ACTION_OFFSET = 2_000_000_000
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
            ACTION_EVENT_END -> handleEventEnd(context, intent)
            ACTION_ARCHIVE_CONFIRM -> handleArchiveConfirm(context, intent)
            ACTION_EVENT_END_DISMISS -> handleEventEndDismiss(context, intent)
        }
    }

    /**
     * 每日晨报：查当天/最近事项，生成俏皮早安通知，并排定次日闹钟
     *
     * 关键顺序：**先排次日闹钟，再发通知**。
     * 历史上通知生成抛异常（如 DB 未就绪、通知渠道异常）会导致后续 scheduleDailyDigest
     * 永远不被执行，闹钟链条就此断裂 —— 表现为「不是每天都推送」。
     * 现在两步各自独立 try/catch，任一步失败都不影响另一步。
     */
    private fun handleMorningDigest(context: Context) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 0. 先做每日维护：归档到期日程 + 清理过期回收站，让本次晨报与历史基于最新数据
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    CalendarAutoArchiver.scanAndArchive(appContext)
                    db.taskDao().purgeExpiredTrash(
                        System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "每日归档/清理失败(不影响晨报通知): ${e.message}", e)
                }

                // 1. 先把次日闹钟排上，保证链条永不中断
                try {
                    MorningDigestScheduler.scheduleDailyDigest(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "晨报次日闹钟排期失败(不影响本次通知): ${e.message}", e)
                }

                // 2. 再发本次通知；失败也绝不影响上面的闹钟链条
                try {
                    showMorningDigest(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "晨报通知生成失败: ${e.message}", e)
                }
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

    /**
     * 日程结束时刻：弹"是否已完成"确认通知。
     * 用户点"已完成"→ ACTION_ARCHIVE_CONFIRM 立即归档；点"稍后"或忽略 → 通知消失，
     * 第三天晨报由 CalendarAutoArchiver 兜底归档。
     */
    private fun handleEventEnd(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "日程"

        val doneIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ARCHIVE_CONFIRM
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            taskId.toInt() + ARCHIVE_ACTION_OFFSET,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_EVENT_END_DISMISS
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            taskId.toInt() + ARCHIVE_ACTION_OFFSET + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "家庭、健康、生活事项及日程的准时与提前提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.cuon.app.R.drawable.ic_notification)
            .setContentTitle("日程已结束：$title")
            .setContentText("完成了吗？")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, "已完成", donePending)
            .addAction(0, "稍后", dismissPending)
            .build()

        notificationManager.notify(taskId.toInt() + END_ALARM_OFFSET, notification)
    }

    /** 用户点"稍后"：仅关闭结束确认通知，不改数据（第三天晨报兜底归档） */
    private fun handleEventEndDismiss(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(taskId.toInt() + END_ALARM_OFFSET)
    }

    /** 用户在结束确认通知上点"已完成"：立即归档该日程 */
    private fun handleArchiveConfirm(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = AppDatabase.getDatabase(appContext).taskDao().getTaskById(taskId)
                if (task != null && task.isCalendarEvent && !task.isCompleted) {
                    CalendarAutoArchiver.archive(appContext, AppDatabase.getDatabase(appContext).taskDao(), task, System.currentTimeMillis())
                    // 归档后取消结束通知，避免残留
                    (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                        ?.cancel(taskId.toInt() + END_ALARM_OFFSET)
                }
            } catch (e: Exception) {
                Log.e(TAG, "归档确认失败: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
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
