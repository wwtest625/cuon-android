package com.cuon.app.data.remote

import org.junit.Assert.*
import org.junit.Test

/**
 * UserContext 档案文本注入单测：确保 prompt 段落拼装规则正确、空上下文不污染提示。
 */
class UserContextTest {

    @Test
    fun `空上下文不生成 prompt 段`() {
        assertTrue(UserContext().isEmpty)
        assertEquals("", UserContext().toPromptBlock())
    }

    @Test
    fun `只有默认城市也能注入`() {
        val ctx = UserContext(defaultCity = "杭州")
        assertFalse(ctx.isEmpty)
        val block = ctx.toPromptBlock()
        assertTrue("应含【用户档案】抬头", block.contains("【用户档案】"))
        assertTrue("应列出默认城市", block.contains("默认城市：杭州"))
        assertFalse("无地点时不应出现地点小节", block.contains("常用地点"))
    }

    @Test
    fun `常用地点逐条列出 label 地址 城市`() {
        val ctx = UserContext(
            defaultCity = "杭州",
            frequentPlaces = listOf(
                UserContext.FrequentPlace("家", "浙江省杭州市西湖区文三路100号", "杭州"),
                UserContext.FrequentPlace("公司", "杭州未来科技城海创园", "杭州"),
                UserContext.FrequentPlace("孩子学校", "杭州市学军小学", "杭州")
            )
        )
        val block = ctx.toPromptBlock()
        for (label in listOf("家", "公司", "孩子学校")) {
            assertTrue("应含 $label", block.contains(label))
        }
        assertTrue("地址应原样出现", block.contains("文三路100号"))
        assertTrue("城市括注应出现", block.contains("（杭州）"))
    }

    @Test
    fun `三条注入规则的关键触发词都在文本里`() {
        val block = UserContext(
            defaultCity = "杭州",
            frequentPlaces = listOf(UserContext.FrequentPlace("家", "文三路", "杭州"))
        ).toPromptBlock()
        // 规则 1：指代词回填 location
        assertTrue("应提到指代词回填 location", block.contains("回家") && block.contains("location"))
        // 规则 2：省略城市按默认补全
        assertTrue("应提到未说城市按默认补全", block.contains("未说城市") && block.contains("默认城市"))
        // 规则 3：冲突以输入为准
        assertTrue("应说明输入优先", block.contains("以输入为准"))
    }
}
