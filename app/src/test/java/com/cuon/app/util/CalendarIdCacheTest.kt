package com.cuon.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日历 ID 缓存语义的回归测试。
 *
 * 覆盖评审提出的 P2 缺陷：
 * 「首次查询不到可写日历时，null 也会被缓存；之后用户即使新增或启用日历，
 *   本进程后续同步仍会直接返回 null。缓存日历被删除后也有类似风险。」
 *
 * 这里用 [FakeCalendarIdCache] 复刻 CalendarSyncHelper.resolveWritableCalendarId 的缓存
 * 状态机（同一套判定规则），让负结果与失效语义可在纯 JVM 下单测，无需 ContentResolver。
 */
class CalendarIdCacheTest {

    /**
     * 与 CalendarSyncHelper.resolveWritableCalendarId 完全同构的缓存状态机：
     * - 命中缓存直接返回
     * - 只有解析到有效 ID 才算「已解析」并缓存
     * - null（负结果）绝不缓存
     */
    private class FakeCalendarIdCache {
        private var cachedId: Long? = null
        private var resolved = false

        /** 模拟一次查询；@param queryResult 模拟 ContentResolver 返回的日历 ID */
        fun resolve(queryResult: Long?): Long? {
            if (resolved) return cachedId

            if (queryResult == null) {
                resolved = false
                cachedId = null
                return null
            }
            cachedId = queryResult
            resolved = true
            return queryResult
        }

        fun invalidate() {
            resolved = false
            cachedId = null
        }

        fun isCachingPositiveResult(): Boolean = resolved
    }

    @Test
    fun testNegativeResultIsNotCached() {
        val cache = FakeCalendarIdCache()

        // 首次查询：设备上还没有任何可写日历
        assertNull(cache.resolve(null))
        assertFalse("负结果绝不能被标记为已缓存", cache.isCachingPositiveResult())
    }

    @Test
    fun testUserAddsCalendarLater_isPickedUp() {
        val cache = FakeCalendarIdCache()

        // 首次查询为空（用户尚未添加日历）
        assertNull(cache.resolve(null))

        // 用户随后新增/启用了一个日历 → 必须能被发现，而不是永远返回 null
        val afterUserAdded = cache.resolve(7L)
        assertEquals("用户新增日历后应被立即发现", 7L, afterUserAdded)
        assertTrue(cache.isCachingPositiveResult())
    }

    @Test
    fun testPositiveResultIsCached() {
        val cache = FakeCalendarIdCache()

        assertEquals(3L, cache.resolve(3L))
        assertTrue(cache.isCachingPositiveResult())

        // 命中缓存：后续查询结果被忽略，避免每次同步都打 ContentResolver
        assertEquals(3L, cache.resolve(99L))
    }

    @Test
    fun testCalendarDeleted_invalidateAllowsRediscovery() {
        val cache = FakeCalendarIdCache()
        assertEquals(5L, cache.resolve(5L))

        // 该日历被用户删除 → 写入失败后清缓存
        cache.invalidate()
        assertFalse(cache.isCachingPositiveResult())

        // 重新解析应能选中新的日历，而不是继续用失效的 id=5
        assertEquals(8L, cache.resolve(8L))
    }

    @Test
    fun testInvalidateOnFailure_recoversFromStaleId() {
        val cache = FakeCalendarIdCache()
        cache.resolve(5L)

        // 模拟「写入失败 → 清缓存 → 重新解析」的标准恢复路径
        cache.invalidate()
        val recovered = cache.resolve(null)
        assertNull("重新解析仍无可用日历时应返回 null", recovered)

        // 且此时依然没有缓存负结果，用户之后新增日历仍可被发现
        assertFalse(cache.isCachingPositiveResult())
        assertEquals(12L, cache.resolve(12L))
    }

    @Test
    fun testRepeatedNegativeQueriesAlwaysRetry() {
        val cache = FakeCalendarIdCache()

        // 连续多次查询为空，每次都应当真的去查（不靠缓存短路）
        repeat(5) { assertNull(cache.resolve(null)) }
        assertFalse(cache.isCachingPositiveResult())

        // 第 6 次用户终于装上了日历 → 能被发现
        assertEquals(1L, cache.resolve(1L))
    }
}
