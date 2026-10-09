package com.cuon.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 直接驱动 CalendarSyncHelper.insertEventWithRetry 的回归测试。
 *
 * 覆盖评审提出的 P2 缺陷：
 * 「插入返回空 URI 时会清缓存，但随后立即 return eventId，因此首次插入返回空 URI
 *   就直接结束，不会重新解析日历并重试。建议仅在还有重试次数时继续循环。」
 *
 * 核心不变量：**空 URI 必须触发「清缓存 + 重新解析 + 再次插入」**。
 */
class CalendarSyncRetryTest {

    /**
     * 测试替身：可编排每一次 resolve / insert 的返回值，并记录真实调用序列。
     */
    private class FakeSync {
        val resolveCalls = mutableListOf<Long?>()
        val insertCalls = mutableListOf<Long>()
        var invalidateCount = 0

        /** 每次 resolve 依次返回这些值（用尽后重复最后一个） */
        var resolveQueue: List<Long?> = listOf(5L)

        /** 每次 insert 依次返回这些 eventId（null 表示空 URI） */
        var insertQueue: List<Long?> = listOf(42L)

        /** 是否让某次 insert 抛异常（按 insert 次数，1-based） */
        var throwOnInsertAttempt: Set<Int> = emptySet()

        private fun resolve(): Long? {
            val idx = resolveCalls.size
            val value = resolveQueue.getOrElse(idx) { resolveQueue.lastOrNull() }
            resolveCalls += value
            return value
        }

        private fun insert(calendarId: Long): Long? {
            insertCalls += calendarId
            val attemptNo = insertCalls.size
            if (attemptNo in throwOnInsertAttempt) throw IllegalStateException("模拟插入异常")
            return insertQueue.getOrElse(attemptNo - 1) { insertQueue.lastOrNull() }
        }

        fun run(maxAttempts: Int = 2): Long? =
            CalendarSyncHelper.insertEventWithRetry(
                maxAttempts = maxAttempts,
                resolveCalendarId = { resolve() },
                insertEvent = { id -> insert(id) },
                onInvalidateCache = { invalidateCount++ }
            )
    }

    @Test
    fun testEmptyUriOnFirstAttempt_retriesAndSucceeds() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L, 9L)      // 重试时解析到新日历
            insertQueue = listOf(null, 42L)    // 首次空 URI，第二次成功
        }

        val result = fake.run()

        assertEquals("重试后应拿到 eventId", 42L, result)
        assertEquals("必须重新解析日历", 2, fake.resolveCalls.size)
        assertEquals("必须再次插入", 2, fake.insertCalls.size)
        assertEquals("第二次应使用重新解析出的日历 ID", 9L, fake.insertCalls[1])
        assertTrue("空 URI 必须清缓存", fake.invalidateCount >= 1)
    }

    @Test
    fun testEmptyUriOnLastAttempt_returnsNull() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L, 9L)
            insertQueue = listOf(null, null)   // 两次都是空 URI
        }

        val result = fake.run()

        assertEquals("用尽次数后返回 null", null, result)
        assertEquals(2, fake.resolveCalls.size)
        assertEquals(2, fake.insertCalls.size)
    }

    @Test
    fun testEmptyUri_mustNotStopAfterFirstAttempt() {
        // 这条用例专门锁死评审指出的缺陷：首次空 URI 不能提前结束
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L, 9L)
            insertQueue = listOf(null, 42L)
        }

        fake.run()

        assertTrue(
            "首次空 URI 后必须发生第二次插入（此前缺陷是直接 return 结束）",
            fake.insertCalls.size == 2
        )
    }

    @Test
    fun testThrownExceptionOnFirstAttempt_retriesAndSucceeds() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L, 9L)
            insertQueue = listOf(null, 77L)
            throwOnInsertAttempt = setOf(1)   // 首次抛异常
        }

        val result = fake.run()

        assertEquals(77L, result)
        assertEquals(2, fake.insertCalls.size)
        assertTrue("异常必须清缓存", fake.invalidateCount >= 1)
    }

    @Test
    fun testSuccessOnFirstAttempt_noRetry() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L)
            insertQueue = listOf(42L)
        }

        val result = fake.run()

        assertEquals(42L, result)
        assertEquals("一次成功不应重复解析", 1, fake.resolveCalls.size)
        assertEquals(1, fake.insertCalls.size)
        assertEquals("成功路径不应清缓存", 0, fake.invalidateCount)
    }

    @Test
    fun testNoWritableCalendar_givesUpWithoutInserting() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(null)   // 始终没有可写日历
            insertQueue = listOf(42L)
        }

        val result = fake.run()

        assertEquals(null, result)
        assertEquals("无可用日历时不应尝试插入", 0, fake.insertCalls.size)
    }

    @Test
    fun testPermissionMissingOnRetry_givesUp() {
        // 首次空 URI 后权限被撤销 → resolve 返回 null → 应放弃而不是无限重试
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L, null)
            insertQueue = listOf(null, 42L)
        }

        val result = fake.run()

        assertEquals(null, result)
        assertEquals("第二次 resolve 返回 null 后必须终止", 2, fake.resolveCalls.size)
        assertEquals(1, fake.insertCalls.size)
    }

    @Test
    fun testSingleAttemptConfigured_emptyUriReturnsNullImmediately() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L)
            insertQueue = listOf(null)
        }

        val result = fake.run(maxAttempts = 1)

        assertEquals(null, result)
        assertEquals(1, fake.insertCalls.size)
        assertEquals(1, fake.invalidateCount)
    }

    @Test
    fun testEveryFailedAttemptInvalidatesCache() {
        val fake = FakeSync().apply {
            resolveQueue = listOf(5L, 9L, 11L)
            insertQueue = listOf(null, null, null)
        }

        fake.run(maxAttempts = 3)

        assertEquals("每次失败都要清缓存", 3, fake.invalidateCount)
        assertEquals(3, fake.insertCalls.size)
    }
}
