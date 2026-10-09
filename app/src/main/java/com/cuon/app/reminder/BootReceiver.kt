package com.cuon.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val shouldReschedule = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED

        if (shouldReschedule) {
            Log.i("BootReceiver", "触发闹钟重排: action=$action")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appContext = context.applicationContext
                    ReminderScheduler.rescheduleAllFutureReminders(appContext)
                    // 自愈式重排：闹钟丢失或基准时间漂移（时区/手动改时间）都能被纠正
                    MorningDigestScheduler.ensureDailyDigest(appContext)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "重排提醒失败: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
