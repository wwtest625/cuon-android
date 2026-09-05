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
    fun testParseLocally_recognizesCalendarAndPriority() {
        val input = "明天下午两点在星巴克开会讨论需求；紧急提交周报；买两瓶牛奶"
        val items = aiParserService.parseLocally(input)

        assertEquals("应成功拆解出 3 个事项", 3, items.size)

        // 验证第1项：包含“开会”，应识别为日程事件
        assertTrue("包含开会应为日程", items[0].isCalendarEvent)
        assertEquals("日程", items[0].tag)

        // 验证第2项：包含“紧急”，优先级应为 high
        assertFalse("提交周报不是日程", items[1].isCalendarEvent)
        assertEquals("包含紧急应为高优", "high", items[1].priority)

        // 验证第3项：普通事项，默认 medium
        assertFalse("买牛奶不是日程", items[2].isCalendarEvent)
        assertEquals("普通事项为中等优先级", "medium", items[2].priority)
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
