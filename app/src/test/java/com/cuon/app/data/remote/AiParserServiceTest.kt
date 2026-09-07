package com.cuon.app.data.remote

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiParserServiceTest {

    private lateinit var aiParserService: AiParserService

    @Before
    fun setUp() {
        aiParserService = AiParserService(
            apiKey = "",
            apiBaseUrl = "https://example.com/v1",
            modelName = "test-model"
        )
    }

    @Test
    fun testCleanMarkdownJson_withCodeFences() {
        val raw = """
            ```json
            {
              "items": [
                {"title": "开会讨论架构", "isCalendarEvent": true}
              ]
            }
            ```
        """.trimIndent()

        val cleaned = aiParserService.cleanMarkdownJson(raw)
        assertTrue("清洗后的字符串应以 { 开头", cleaned.startsWith("{"))
        assertTrue("清洗后的字符串应以 } 结尾", cleaned.endsWith("}"))
        assertFalse("不应再包含 markdown 反引号", cleaned.contains("```"))
    }

    @Test
    fun testCleanMarkdownJson_withSurroundingText() {
        val raw = "好的，这是为您拆解的事项：{\"items\": []}，请查收！"
        val cleaned = aiParserService.cleanMarkdownJson(raw)
        assertEquals("{\"items\": []}", cleaned)
    }

    @Test
    fun testFallbackToRawTask_honestOutput() {
        // 方案B：AI 不可用时兜底不做任何猜测式拆解，原文存为单条普通待办
        val input = "明天下午两点开会讨论需求；紧急提交周报；买两瓶牛奶"
        val items = aiParserService.fallbackToRawTask(input, "网络超时")

        assertEquals("兜底应只产出单条原文待办", 1, items.size)
        assertEquals("标题应保留用户原文", input, items[0].title)
        assertFalse("兜底不应猜测日程类型", items[0].isCalendarEvent)
        assertEquals("待办", items[0].tag)
    }

    @Test
    fun testParseDateStringToMillis_validAndInvalid() {
        val validDateStr = "2026-09-06 14:00:00"
        val millis = aiParserService.parseDateStringToMillis(validDateStr)
        assertNotNull("合法日期应成功解析为时间戳", millis)
        assertTrue("时间戳应大于0", millis!! > 0)

        val invalidDateStr = "not-a-date"
        val invalidMillis = aiParserService.parseDateStringToMillis(invalidDateStr)
        assertNull("非法格式应返回 null", invalidMillis)

        val nullMillis = aiParserService.parseDateStringToMillis(null)
        assertNull("null 输入应返回 null", nullMillis)
    }
}
