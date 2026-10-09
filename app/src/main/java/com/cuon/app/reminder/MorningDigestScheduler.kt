package com.cuon.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.cuon.app.MainActivity
import java.util.Calendar

/**
 * 晨报闹钟对账结论
 */
enum class DigestReconcileAction {
    /** 闹钟存活且与预期一致，什么都不做 */
    KEEP,

    /** 落盘触发时间已过期，但仍在投递宽限期内 —— 闹钟可能正在投递，绝不能覆盖 */
    PENDING_DELIVERY,

    /** 闹钟确实丢失或基准已漂移，需要重排 */
    RESCHEDULE
}

/**
 * 每日早 9 点晨报闹钟：结合当天日程与待办，发一条俏皮不烦的早安摸底通知。
 *
 * ## 可靠性设计（修历史「不是每天都推送」缺陷）
 *
 * 1. **闹钟链条单点故障已消除**：触发后的「排次日闹钟」不再依赖通知是否发送成功。
 *    AlarmReceiver 中先排次日、再发通知，且各自独立 try/catch，通知抛异常也不会中断链条。
 * 2. **优先级兜底排程**：
 *    - 有精确闹钟权限 → `setExactAndAllowWhileIdle`（精确且不在状态栏显示闹钟图标）；
 *    - 无权限（Android 13+ 新装应用默认拒绝 SCHEDULE_EXACT_ALARM）→ 降级 `setAlarmClock`。
 *    `setAlarmClock` 是唯一「免权限 + 不受 Doze 限制 + 准时触发」的 API，
 *    避免原先降级到 `setAndAllowWhileIdle` 后被 Doze 推迟数小时甚至跨天丢失。
 * 3. **自愈不误伤**：`ensureDailyDigest()` 用 [reconcileDigest] 区分「闹钟正在投递途中」
 *    与「真的丢失」。刚过 9 点时即使 expected 已顺延到次日，也不会覆盖当天待处理的闹钟。
 * 4. **落盘对账**：把「下一次预期触发时间」写入 SharedPreferences，便于排查与时间变更后重建。
 *
 * requestCode 采用高位隔离，避免与单任务提醒的 taskId.toInt() 冲突。
 */
object MorningDigestScheduler {

    private const val TAG = "MorningDigest"
    const val ACTION_MORNING_DIGEST = "com.cuon.app.ACTION_MORNING_DIGEST"

    private const val DIGEST_REQUEST_CODE = 19000009
    const val DIGEST_HOUR_OF_DAY = 9

    /**
     * 投递宽限期：闹钟触发后，从「预期触发时刻」到「广播可能仍在派发/投递」的容忍窗口。
     *
     * 作用：9:00 整闹钟响铃、但 AlarmReceiver 尚未跑完时，若用户恰好启动 App 触发 ensureDailyDigest()，
     * 此时 expected 已顺延到次日 9:00，而落盘值仍是当天 9:00。必须靠这个窗口识别出
     * 「当天闹钟正在投递中」，从而保留它，而不是被次日闹钟覆盖掉当天的通知。
     *
     * 取 30 分钟：足够覆盖广播派发与进程冷启动，又不会长到让真正丢失的闹钟迟迟不被修复。
     */
    const val DELIVERY_GRACE_MILLIS = 30 * 60 * 1000L

    private const val PREFS_NAME = "cuon_digest_prefs"
    private const val KEY_NEXT_TRIGGER_AT = "next_trigger_at"

    /**
     * 纯函数：给定当前时间，算出下一个 9:00 的触发时间戳（今天 9 点已过则取明天）。
     * 独立成函数便于单元测试，不依赖 AlarmManager / Context。
     */
    fun computeNextTrigger(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, DIGEST_HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 纯函数：对账决策，决定 [ensureDailyDigest] 该怎么做。
     *
     * @param alarmAlive 系统里是否还存在存活的晨报 PendingIntent
     * @param storedTriggerAt 落盘的预期触发时间（0 表示从未成功落盘）
     * @param now 当前时间戳
     * @return 见 [DigestReconcileAction]
     */
    fun reconcileDigest(alarmAlive: Boolean, storedTriggerAt: Long, now: Long): DigestReconcileAction {
        // 闹钟不存在 → 必须重排（含 stored=0 的未知状态，宁可重排也不要漏推）
        if (!alarmAlive) return DigestReconcileAction.RESCHEDULE

        val expected = computeNextTrigger(now)

        // 与预期完全一致 → 无需任何动作
        if (storedTriggerAt == expected) return DigestReconcileAction.KEEP

        // stored 落在「过去但仍在投递宽限期内」→ 闹钟刚响过、广播可能正在投递，绝不能覆盖
        if (storedTriggerAt > 0L) {
            val sinceStored = now - storedTriggerAt
            if (sinceStored in 0 until DELIVERY_GRACE_MILLIS) {
                return DigestReconcileAction.PENDING_DELIVERY
            }
        }

        // 其余不一致：时间/时区跳变、或跨过一天未及时重排 → 重排
        return DigestReconcileAction.RESCHEDULE
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun buildPendingIntent(context: Context, create: Boolean): PendingIntent? {
        val appContext = context.applicationContext
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_MORNING_DIGEST
        }
        var flags = PendingIntent.FLAG_IMMUTABLE
        flags = if (create) flags or PendingIntent.FLAG_UPDATE_CURRENT else flags or PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(appContext, DIGEST_REQUEST_CODE, intent, flags)
    }

    /** 点击状态栏闹钟图标时打开的界面 */
    private fun buildShowIntent(context: Context): PendingIntent? {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SELECTED_DATE", System.currentTimeMillis())
        }
        return PendingIntent.getActivity(
            appContext,
            DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 当前是否已存在存活的晨报闹钟 */
    fun isScheduled(context: Context): Boolean = buildPendingIntent(context, create = false) != null

    /** 落盘的下一次预期触发时间（0 表示未落盘） */
    fun getStoredTriggerAt(context: Context): Long =
        prefs(context).getLong(KEY_NEXT_TRIGGER_AT, 0L)

    /**
     * 排下一个早 9 点的晨报闹钟（幂等，可反复调用）
     */
    fun scheduleDailyDigest(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerTime = computeNextTrigger(System.currentTimeMillis())
        val pendingIntent = buildPendingIntent(appContext, create = true) ?: return

        val scheduled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // 无精确闹钟权限：用 setAlarmClock 保证准时（免权限、不受 Doze 限制）
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, buildShowIntent(appContext)),
                    pendingIntent
                )
                Log.w(TAG, "无精确闹钟权限，晨报改用 setAlarmClock 保准时: trigger=$triggerTime")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.i(TAG, "晨报闹钟已排期: trigger=$triggerTime")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "晨报闹钟缺少权限: ${e.message}")
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, buildShowIntent(appContext)),
                    pendingIntent
                )
                Log.w(TAG, "晨报已降级为 setAlarmClock: trigger=$triggerTime")
                true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "晨报降级排程亦失败: ${fallbackEx.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "晨报闹钟排期失败: ${e.message}", e)
            false
        }

        if (scheduled) {
            prefs(appContext).edit().putLong(KEY_NEXT_TRIGGER_AT, triggerTime).apply()
        }
    }

    /**
     * 自愈入口：App 启动 / 开机 / 应用更新 / 时区与时间变更时调用。
     *
     * 关键保护：若落盘触发时间刚过期（说明当天闹钟可能正在投递途中），
     * 即使 expected 已顺延到次日也**绝不重排**，避免覆盖掉当天待处理的通知。
     */
    fun ensureDailyDigest(context: Context) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()

        val action = reconcileDigest(
            alarmAlive = isScheduled(appContext),
            storedTriggerAt = getStoredTriggerAt(appContext),
            now = now
        )

        when (action) {
            DigestReconcileAction.KEEP -> {
                // 闹钟健在且与预期一致，不打扰
            }

            DigestReconcileAction.PENDING_DELIVERY -> {
                // 当天闹钟刚触发、广播可能正在投递：保留它，交给 AlarmReceiver 完成后续排程
                Log.i(TAG, "晨报闹钟正在投递宽限期内，保留当天触发不覆盖: stored=${getStoredTriggerAt(appContext)}")
            }

            DigestReconcileAction.RESCHEDULE -> {
                if (!isScheduled(appContext)) {
                    Log.w(TAG, "晨报闹钟已丢失，立即重排")
                } else {
                    Log.i(
                        TAG,
                        "晨报触发时间对账不一致 (stored=${getStoredTriggerAt(appContext)}, " +
                            "expected=${computeNextTrigger(now)})，重排"
                    )
                }
                scheduleDailyDigest(appContext)
            }
        }
    }

    /** 取消晨报闹钟（用于设置开关等场景） */
    fun cancelDailyDigest(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        buildPendingIntent(appContext, create = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.i(TAG, "已取消晨报闹钟")
        }
        prefs(appContext).edit().remove(KEY_NEXT_TRIGGER_AT).apply()
    }
}
