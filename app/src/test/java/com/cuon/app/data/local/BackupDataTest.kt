package com.cuon.app.data.local

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 备份 JSON 往返测试：保证实体可被 Gson 完整序列化/反序列化 */
class BackupDataTest {

    private val gson = Gson()

    @Test
    fun `round trip preserves all task fields`() {
        val task = TaskEntity(
            id = 7,
            title = "带孩子打疫苗",
            description = "社区医院",
            isCalendarEvent = true,
            startTime = 1700000000000,
            endTime = 1700003600000,
            isCompleted = false,
            completedAt = null,
            deletedAt = null,
            priority = "high",
            tag = "健康",
            location = "家",
            reminderMinutesBefore = 30,
            recurrenceRule = "MONTHLY",
            createdAt = 1699000000000
        )
        val backup = BackupData(
            exportedAt = 1700100000000,
            defaultCity = "杭州",
            tasks = listOf(task),
            places = listOf(FrequentPlaceEntity(id = 1, label = "家", address = "浙江省杭州市西湖区文三路", city = "杭州"))
        )

        val restored = gson.fromJson(gson.toJson(backup), BackupData::class.java)

        assertEquals(BackupData.CURRENT_VERSION, restored.version)
        assertEquals("杭州", restored.defaultCity)
        assertEquals(1, restored.tasks.size)
        assertEquals(task, restored.tasks[0])
        assertEquals(1, restored.places.size)
        assertEquals("家", restored.places[0].label)
    }

    @Test
    fun `soft deleted and completed tasks keep timestamps`() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "已删", deletedAt = 1699999999999),
            TaskEntity(id = 2, title = "已完", isCompleted = true, completedAt = 1699999998888)
        )
        val restored = gson.fromJson(gson.toJson(BackupData(tasks = tasks)), BackupData::class.java)

        assertEquals(1699999999999, restored.tasks[0].deletedAt)
        assertNull(restored.tasks[0].completedAt)
        assertEquals(1699999998888, restored.tasks[1].completedAt)
        assertNull(restored.tasks[1].deletedAt)
    }

    @Test
    fun `empty backup serializes with default city blank`() {
        val json = gson.toJson(BackupData())
        val restored = gson.fromJson(json, BackupData::class.java)
        assertEquals("", restored.defaultCity)
        assertEquals(0, restored.tasks.size)
        assertEquals(0, restored.places.size)
    }
}
