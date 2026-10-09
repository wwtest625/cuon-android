package com.cuon.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 晨报自愈对账决策的回归测试。
 *
 * 覆盖评审提出的 P2 缺陷：
 * 「闹钟已到期、接收广播尚未处理时，若应用启动调用 ensureDailyDigest()，
 *   expected 已变为次日 9 点而落盘值仍是当天 9 点，代码会重排次日闹钟、替换当天待处理的闹钟」
 *
 * 核心不变量：**落盘触发时间刚过期时必须保留当天闹钟，绝不可覆盖**。
 */
class MorningDigestReconcileTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // 当天 9:00 已触发，落盘值就是当天 9:00
    private val todayNine = at(2026, Calendar.SEPTEMBER, 10, 9, 0)

    @Test
    fun testAlarmJustFired_preservesTodayTrigger() {
        // 9:00 整闹钟响铃，1 秒后 App 启动 → 必须保留当天闹钟，不能重排到次日
        val now = todayNine + 1_000L
        assertEquals(
            "闹钟刚触发必须保留，绝不能覆盖当天待投递的通知",
            DigestReconcileAction.PENDING_DELIVERY,
            MorningDigestScheduler.reconcileDigest(
                alarmAlive = true,
                storedTriggerAt = todayNine,
                now = now
            )
        )
    }

    @Test
    fun testBroadcastStillInFlight_withinGraceWindow() {
        // 9:00 响铃，29 分 59 秒后广播仍在投递 → 仍在宽限期内，保留
        val now = todayNine + MorningDigestScheduler.DELIVERY_GRACE_MILLIS - 1_000L
        assertEquals(
            DigestReconcileAction.PENDING_DELIVERY,
            MorningDigestScheduler.reconcileDigest(true, todayNine, now)
        )
    }

    @Test
    fun testGraceWindowExpired_reschedules() {
        // 超过宽限期仍未被处理 → 认定真的丢了，重排
        val now = todayNine + MorningDigestScheduler.DELIVERY_GRACE_MILLIS + 1_000L
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(true, todayNine, now)
        )
    }

    @Test
    fun testGraceWindowBoundary_isExclusive() {
        // 恰好等于宽限期（now - stored == GRACE）→ 窗口为左闭右开，此处已出窗口，重排
        val now = todayNine + MorningDigestScheduler.DELIVERY_GRACE_MILLIS
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(true, todayNine, now)
        )
    }

    @Test
    fun testLastMillisInsideWindow_isPending() {
        // 窗口内最后一毫秒（GRACE - 1ms）→ 仍保留当天闹钟
        val now = todayNine + MorningDigestScheduler.DELIVERY_GRACE_MILLIS - 1L
        assertEquals(
            DigestReconcileAction.PENDING_DELIVERY,
            MorningDigestScheduler.reconcileDigest(true, todayNine, now)
        )
    }

    @Test
    fun testHealthyAlarm_keepsWithoutAction() {
        // 正常态：明天 9 点的闹钟存活，落盘值与 expected 一致 → 不动
        val now = at(2026, Calendar.SEPTEMBER, 10, 20, 0)   // 今晚 20:00
        val tomorrowNine = MorningDigestScheduler.computeNextTrigger(now)
        assertEquals(
            DigestReconcileAction.KEEP,
            MorningDigestScheduler.reconcileDigest(true, tomorrowNine, now)
        )
    }

    @Test
    fun testAlarmLost_reschedules() {
        // 闹钟被系统清理 → 无论落盘值如何都必须重排
        val now = at(2026, Calendar.SEPTEMBER, 10, 20, 0)
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(
                alarmAlive = false,
                storedTriggerAt = MorningDigestScheduler.computeNextTrigger(now),
                now = now
            )
        )
    }

    @Test
    fun testAlarmLostAndJustFired_stillReschedules() {
        // 闹钟不存在时，「刚过期」不再构成保护理由：丢了就该重排
        val now = todayNine + 1_000L
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(false, todayNine, now)
        )
    }

    @Test
    fun testTimezoneShift_reschedules() {
        // 时区变更：闹钟存活，但落盘时间与新的 expected 不符且已远超宽限期 → 重排
        val now = at(2026, Calendar.SEPTEMBER, 12, 20, 0)
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(true, todayNine, now)
        )
    }

    @Test
    fun testStoredInFuture_neverPendingDelivery() {
        // stored 落在未来（时钟被往回拨）→ 不应被判定为「正在投递」，应重排对齐新基准
        val now = at(2026, Calendar.SEPTEMBER, 10, 10, 0)
        val futureStored = at(2026, Calendar.SEPTEMBER, 15, 9, 0)
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(true, futureStored, now)
        )
    }

    @Test
    fun testNeverStoredButAlive_reschedules() {
        // 落盘值从未写入（0，如老版本升级而来）但闹钟存活 → 保守重排以完成落盘对账
        val now = at(2026, Calendar.SEPTEMBER, 10, 20, 0)
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(true, 0L, now)
        )
    }

    @Test
    fun testJustAfterMidnight_preservesPriorDayTrigger() {
        // 跨日场景：9:00 触发后直到 23:59 才被处理，已远超宽限期 → 重排（当天通知确实丢了）
        val now = at(2026, Calendar.SEPTEMBER, 10, 23, 59)
        assertEquals(
            DigestReconcileAction.RESCHEDULE,
            MorningDigestScheduler.reconcileDigest(true, todayNine, now)
        )
    }
}
