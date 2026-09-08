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
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("BootReceiver", "系统启动或应用更新，开始重排未来生活事项提醒: action=$action")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ReminderScheduler.rescheduleAllFutureReminders(context.applicationContext)
                    MorningDigestScheduler.scheduleDailyDigest(context.applicationContext)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "重排提醒失败: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
