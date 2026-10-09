package com.cuon.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 每日晨报闹钟触发时间计算的单元测试。
 * 重点覆盖「不是每天都推送」缺陷的时间基准逻辑：
 * 9 点已过必须顺延到次日，且必须严格落在未来。
 */
class MorningDigestSchedulerTest {

    private fun makeTime(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, second)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun testBeforeNineOClock_schedulesTodayNine() {
        // 早上 6:00 → 应当排到今天 9:00
        val now = makeTime(2026, Calendar.SEPTEMBER, 10, 6, 0)
        val expected = makeTime(2026, Calendar.SEPTEMBER, 10, 9, 0)
        assertEquals(expected, MorningDigestScheduler.computeNextTrigger(now))
    }

    @Test
    fun testAfterNineOClock_schedulesTomorrowNine() {
        // 早上 10:00 → 9 点已过，必须顺延到次日 9:00（否则当天不再触发，表现为「漏推一天」）
        val now = makeTime(2026, Calendar.SEPTEMBER, 10, 10, 0)
        val expected = makeTime(2026, Calendar.SEPTEMBER, 11, 9, 0)
        assertEquals(expected, MorningDigestScheduler.computeNextTrigger(now))
    }

    @Test
    fun testExactlyNineOClock_rollsToTomorrow() {
        // 恰好 9:00:00 整 → 视为已触发，排次日，避免同一天重复触发
        val now = makeTime(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val expected = makeTime(2026, Calendar.SEPTEMBER, 11, 9, 0)
        assertEquals(expected, MorningDigestScheduler.computeNextTrigger(now))
    }

    @Test
    fun testCrossMonthBoundary_rollsCorrectly() {
        // 9 月 30 日 23:00 → 次日应为 10 月 1 日 9:00
        val now = makeTime(2026, Calendar.SEPTEMBER, 30, 23, 0)
        val expected = makeTime(2026, Calendar.OCTOBER, 1, 9, 0)
        assertEquals(expected, MorningDigestScheduler.computeNextTrigger(now))
    }

    @Test
    fun testCrossYearBoundary_rollsCorrectly() {
        // 12 月 31 日 15:00 → 次日应为次年 1 月 1 日 9:00
        val now = makeTime(2026, Calendar.DECEMBER, 31, 15, 0)
        val expected = makeTime(2027, Calendar.JANUARY, 1, 9, 0)
        assertEquals(expected, MorningDigestScheduler.computeNextTrigger(now))
    }

    @Test
    fun testNextTriggerAlwaysStrictlyInFuture() {
        // 任意时刻调用，结果必须严格晚于当前时间（AlarmManager 不接受过去时间）
        val samples = listOf(0, 3, 8, 9, 10, 15, 23).map { hour ->
            makeTime(2026, Calendar.MARCH, 15, hour, 30)
        }
        samples.forEach { now ->
            val next = MorningDigestScheduler.computeNextTrigger(now)
            assertTrue("触发时间必须严格晚于当前时间: now=$now next=$next", next > now)
        }
    }

    @Test
    fun testNextTriggerAlwaysAtNineOClock() {
        // 无论何时调用，触发时刻的时分秒必须恒为 09:00:00.000
        val samples = listOf(1, 7, 9, 12, 20, 23).map { hour ->
            makeTime(2026, Calendar.JUNE, 20, hour, 45, 17)
        }
        samples.forEach { now ->
            val cal = Calendar.getInstance().apply { timeInMillis = MorningDigestScheduler.computeNextTrigger(now) }
            assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, cal.get(Calendar.MINUTE))
            assertEquals(0, cal.get(Calendar.SECOND))
            assertEquals(0, cal.get(Calendar.MILLISECOND))
        }
    }
}
