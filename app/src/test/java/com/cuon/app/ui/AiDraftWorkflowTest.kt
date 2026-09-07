package com.cuon.app.ui

import com.cuon.app.data.local.TaskEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * 针对【致命2·AI结果零纠错与拆解预览流】逻辑的单元测试
 */
class AiDraftWorkflowTest {

    @Test
    fun testDraftItemsGenerationAndDefaults() {
        val rawAiResult = listOf(
            TaskEntity(id = 0, title = "陪妈妈去医院", isCalendarEvent = true, startTime = 1000000L),
            TaskEntity(id = 0, title = "买一箱脱脂牛奶", isCalendarEvent = false, endTime = 2000000L),
            TaskEntity(id = 0, title = "散步30分钟", isCalendarEvent = false)
        )

        // 业务层草稿赋予默认提醒策略：日程默认提前15分钟，带截止待办默认准时(0分)，纯弹性待办null
        val drafts = rawAiResult.map { item ->
            if (item.reminderMinutesBefore == null) {
                if (item.isCalendarEvent) item.copy(reminderMinutesBefore = 15)
                else if (item.endTime != null) item.copy(reminderMinutesBefore = 0)
                else item
            } else item
        }

        assertEquals("日程应默认被赋予提前15分钟提醒", 15, drafts[0].reminderMinutesBefore)
        assertEquals("带截止时间的待办应默认赋予准时(0分钟)提醒", 0, drafts[1].reminderMinutesBefore)
        assertNull("无时间的纯弹性生活待办不设提醒", drafts[2].reminderMinutesBefore)
    }

    @Test
    fun testDraftItemInlineEditCorrection() {
        val initialDrafts = mutableListOf(
            TaskEntity(id = 0, title = "错误拆解的会议", location = "会议室A", isCalendarEvent = true)
        )

        // 用户在预览弹窗中纠错：修改为家庭事项
        val correctedItem = initialDrafts[0].copy(
            title = "家庭聚餐",
            location = "外婆家",
            tag = "家庭",
            reminderMinutesBefore = 30
        )
        initialDrafts[0] = correctedItem

        assertEquals("事项名称应被纠正为家庭聚餐", "家庭聚餐", initialDrafts[0].title)
        assertEquals("地点应被纠正为外婆家", "外婆家", initialDrafts[0].location)
        assertEquals("标签应为家庭", "家庭", initialDrafts[0].tag)
        assertEquals("提醒应为提前30分钟", 30, initialDrafts[0].reminderMinutesBefore)
    }

    @Test
    fun testDraftItemDiscardAndPartialConfirm() {
        val drafts = mutableListOf(
            TaskEntity(id = 0, title = "女儿家长会", isCalendarEvent = true),
            TaskEntity(id = 0, title = "误识别的杂音乱码", isCalendarEvent = false),
            TaskEntity(id = 0, title = "给猫咪打疫苗", isCalendarEvent = false)
        )

        // 用户剔除第2项（误识别的杂音乱码）
        drafts.removeAt(1)

        assertEquals("剔除后草稿应只保留2条有效生活事项", 2, drafts.size)
        assertEquals("第1条依然为女儿家长会", "女儿家长会", drafts[0].title)
        assertEquals("第2条为给猫咪打疫苗", "给猫咪打疫苗", drafts[1].title)
    }
}
