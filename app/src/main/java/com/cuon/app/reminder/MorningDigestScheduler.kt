package com.cuon.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*

/**
 * 每日早 9 点晨报闹钟：结合当天日程与待办，发一条俏皮不烦的早安摸底通知。
 * 触发后在 AlarmReceiver 中排次日闹钟；开机、应用更新与 App 启动时也会重新排期兜底。
 * requestCode 采用高位隔离，避免与单任务提醒的 taskId.toInt() 冲突。
 */
object MorningDigestScheduler {
    private const val TAG = "MorningDigest"
    const val ACTION_MORNING_DIGEST = "com.cuon.app.ACTION_MORNING_DIGEST"

    private const val DIGEST_REQUEST_CODE = 19000009
    private const val DIGEST_HOUR_OF_DAY = 9

    /**
     * 排下一个早 9 点的晨报闹钟（幂等，可反复调用）
     */
    fun scheduleDailyDigest(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, DIGEST_HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MORNING_DIGEST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "无精确闹钟权限，晨报降级为低精度: trigger=$triggerTime")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.i(TAG, "晨报闹钟已排期: trigger=$triggerTime")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "晨报闹钟缺少权限: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "晨报降级闹钟亦失败: ${fallbackEx.message}")
            }
        }
    }
}
