package com.cuon.app.data.remote

/**
 * AI 解析时注入的用户个人上下文：默认城市 + 常用地点簿。
 * 让模型能把"回家/去公司/接孩子"这类指代映射到具体地址，并在缺省城市时自动补全。
 */
data class UserContext(
    val defaultCity: String = "",
    val frequentPlaces: List<FrequentPlace> = emptyList()
) {
    data class FrequentPlace(val label: String, val address: String, val city: String)

    val isEmpty: Boolean get() = defaultCity.isBlank() && frequentPlaces.isEmpty()

    /**
     * 拼装成一段可注入 system prompt 的档案文本；无内容返回空串（调用方自行判断拼接）。
     */
    fun toPromptBlock(): String {
        if (isEmpty) return ""
        return buildString {
            appendLine("【用户档案】")
            if (defaultCity.isNotBlank()) {
                appendLine("默认城市：${defaultCity.trim()}")
            }
            if (frequentPlaces.isNotEmpty()) {
                appendLine("常用地点（听到指代时直接把 location 填为对应完整地址）：")
                for (p in frequentPlaces) {
                    appendLine("  - ${p.label}：${p.address}（${p.city}）")
                }
            }
            appendLine("规则（必须遵守）：")
            appendLine("  1) 只要输入中出现与上面某个 label 相关的表达（如含「家」「公司」「学校」字样：回家、在家吃饭、去公司、接孩子放学），" +
                "该事项的 location 就必须填为对应 label 的完整地址，禁止留空；")
            appendLine("  2) 输入提到某地但未说城市（例如「下周去灵隐寺」），若默认城市存在则按默认城市补全 location；")
            appendLine("  3) 用户档案与输入冲突时以输入为准（例如「去上海公司」不走默认城市与地点簿）。")
        }.trimEnd()
    }
}
