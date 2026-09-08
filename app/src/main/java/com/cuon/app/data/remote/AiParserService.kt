package com.cuon.app.data.remote

import com.cuon.app.data.local.TaskEntity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class ParsedItem(
    @SerializedName("title") val title: String,
    @SerializedName("isCalendarEvent") val isCalendarEvent: Boolean = false,
    @SerializedName("startTime") val startTimeStr: String? = null,
    @SerializedName("endTime") val endTimeStr: String? = null,
    @SerializedName("priority") val priority: String = "medium",
    @SerializedName("location") val location: String = "",
    @SerializedName("tag") val tag: String = "待办"
)

data class AiResponseSchema(
    @SerializedName("items") val items: List<ParsedItem>?
)

/** AI 返回的一次工具调用请求（事项增删改查指令） */
data class AiToolCall(
    val name: String,
    val args: Map<String, Any?> = emptyMap()
)

/** AI 统一处理结果：传统拆解草稿 或 事项管理工具指令 */
sealed class AiOutcome {
    /** 模型未调用工具，按传统拆解返回草稿条目 */
    data class TaskDrafts(val items: List<TaskEntity>) : AiOutcome()

    /** 模型请求执行事项管理工具（增删改查） */
    data class ToolCommands(val calls: List<AiToolCall>) : AiOutcome()
}

class AiParserService(
    private val apiKey: String,
    private val apiBaseUrl: String = "https://apihub.agnes-ai.cn/v1",
    private val modelName: String = "agnes-2.5-flash"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 将输入的自然语言（语音或文本）送入大模型：
     * 记录新事项时拆解为任务草稿；修改/删除/完成/查询已有事项时返回工具调用指令
     */
    suspend fun processInput(input: String): AiOutcome = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext AiOutcome.TaskDrafts(emptyList())

        val now = Date()
        val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINESE).format(now)
        lastFallbackReason = null

        val systemPrompt = """
            你是一个专业的个人生活事项管理助手，帮助用户守护生活中容易疏忽的重要事务。
            用户的工作事项通常不易遗忘，而家庭、健康、纪念日等生活事务一旦疏忽代价很高，请重点保障这类事项被完整拆解、绝不遗漏。
            当前系统基准时间是: $currentTimeString。
            用户输入多来自语音转写，常为口语且带转写噪声（多余空格、语气词、数字混写），你必须：
            1) 将 title 整理为简练的书面用语（如"10 月六号 我结婚"→"举办婚礼"；"买两盒咖啡豆"→"购买咖啡豆"），不得原样照抄口语；
            2) 只要句中出现明确日期或时间（X月X号/X日、明天/后天/下周X、几点钟等），该条必须 isCalendarEvent=true，并将 startTime 精确计算为绝对时间（格式 YYYY-MM-DD HH:mm:ss），未说时刻的默认当天 09:00:00，月日已过今年的推算为明年；
            3) 结婚、婚宴、生日、纪念日、家庭聚会等重要生活事件一律 isCalendarEvent=true 且 priority=high。
            家庭聚会、纪念日、生日、体检、缴费、宠物等事项请给出准确的 startTime 或截止时间。
            4) 用户输入还可能是管理已有事项的指令，请按意图路由到工具：
               - 记录/新增事项 → 调用 add_task（多条事项并行多次调用，title 整理与时间计算规则同上）；
               - 修改/改动已有事项 → 调用 update_task，title_keyword 取用户语句中用于匹配标题的关键词；
               - 完成某事项（"做完了/办好了"）→ 调用 complete_task；
               - 删除某事项 → 调用 delete_task；
               - 查询/盘点事项（"今天有什么事/周几有安排"）→ 调用 query_tasks；
               - 周期性事项（"每周一倒垃圾/每天吃药"）→ add_task 时传 recurrence，并给出下一次发生的绝对时间。
            只有当输入与事项管理完全无关（纯闲聊）时，才返回 items 为空数组的 JSON。
            返回 JSON 时的严格格式：
            {
              "items": [
                {
                  "title": "简练书面语描述（动宾短语）",
                  "isCalendarEvent": true/false (有明确日期时间的聚会、约见、课程、出行、仪式等一律为true；只有截止日的任务为false),
                  "startTime": "YYYY-MM-DD HH:mm:ss或null",
                  "endTime": "YYYY-MM-DD HH:mm:ss或null",
                  "priority": "high/medium/low (纪念日/生日/体检/婚宴/家庭约定等重要生活事项应为high)",
                  "location": "地点，无则留空",
                  "tag": "家庭/健康/个人/财务/纪念/社交 之一"
                }
              ]
            }
        """.trimIndent()

        fun buildRequestBody(toolsEnabled: Boolean): okhttp3.RequestBody {
            val base = buildMap<String, Any?> {
                put("model", modelName)
                put(
                    "messages",
                    listOf(
                        mapOf("role" to "system", "content" to systemPrompt),
                        mapOf("role" to "user", "content" to input)
                    )
                )
                // tools 与 response_format 在部分网关互斥，启用工具时不再要求 JSON 模式
                if (!toolsEnabled) put("response_format", mapOf("type" to "json_object"))
                else put("tools", buildToolSpecs())
            }
            return gson.toJson(base).toRequestBody("application/json".toMediaType())
        }

        // 瞬时故障（超时/网络抖动/5xx/空返回）自动重试 1 次，避免偶发慢响应误入兜底
        // 4xx（如网关不支持 function calling）则去掉工具降级重试
        var lastReason: String? = null
        var toolsEnabled = true

        for (attempt in 1..2) {
            try {
                val request = Request.Builder()
                    .url("$apiBaseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(buildRequestBody(toolsEnabled))
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    if (response.code in 400..499 && toolsEnabled && attempt < 2) {
                        toolsEnabled = false
                        lastReason = "AI 服务拒绝了工具请求 (HTTP ${response.code})"
                        continue
                    }
                    if (response.code >= 500 && attempt < 2) {
                        lastReason = "AI 服务异常 (HTTP ${response.code})"
                        continue
                    }
                    val reason = "AI 服务异常 (HTTP ${response.code})"
                    lastFallbackReason = reason
                    return@withContext AiOutcome.TaskDrafts(fallbackToRawTask(input, reason))
                }

                // 从 chat.completion 结果中解构 content 字段
                val rootJson = gson.fromJson(responseBody, Map::class.java)
                val choices = rootJson["choices"] as? List<*>
                val firstChoice = choices?.firstOrNull() as? Map<*, *>
                val message = firstChoice?.get("message") as? Map<*, *>

                // 优先检查工具调用：模型决定走事项管理（增删改查）路径
                val rawToolCalls = message?.get("tool_calls") as? List<Map<String, Any?>>
                if (!rawToolCalls.isNullOrEmpty()) {
                    val calls = rawToolCalls.mapNotNull { raw ->
                        val fn = raw["function"] as? Map<*, *> ?: return@mapNotNull null
                        val name = (fn["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        val args: Map<String, Any?> = when (val rawArgs = fn["arguments"]) {
                            is String -> try {
                                gson.fromJson(rawArgs, Map::class.java) as? Map<String, Any?>
                            } catch (e: Exception) {
                                null
                            } ?: emptyMap()
                            is Map<*, *> -> rawArgs as? Map<String, Any?>
                            else -> emptyMap()
                        } ?: emptyMap()
                        AiToolCall(name, args)
                    }
                    if (calls.isNotEmpty()) return@withContext AiOutcome.ToolCommands(calls)
                }

                val rawContent = message?.get("content") as? String ?: ""

                // 健壮清洗：提取纯净 JSON，防止 Markdown 围栏符号干扰
                val sanitizedJson = cleanMarkdownJson(rawContent)
                val parsedResponse = gson.fromJson(sanitizedJson, AiResponseSchema::class.java)
                val resultList = mutableListOf<TaskEntity>()

                parsedResponse?.items?.forEach { item ->
                    val startTimestamp = parseDateStringToMillis(item.startTimeStr)
                    val endTimestamp = parseDateStringToMillis(item.endTimeStr)

                    resultList.add(
                        TaskEntity(
                            title = item.title,
                            isCalendarEvent = item.isCalendarEvent,
                            startTime = startTimestamp,
                            endTime = endTimestamp,
                            priority = item.priority.ifBlank { "medium" },
                            location = item.location,
                            tag = item.tag.ifBlank { if (item.isCalendarEvent) "日程" else "待办" }
                        )
                    )
                }

                if (resultList.isNotEmpty()) return@withContext AiOutcome.TaskDrafts(resultList)
                lastReason = "AI 未返回有效结果"
            } catch (e: Exception) {
                e.printStackTrace()
                lastReason = when (e) {
                    is java.net.SocketTimeoutException -> "网络超时"
                    is java.io.IOException -> "网络不可用"
                    else -> "AI 解析异常"
                }
                // 非瞬时异常（如 JSON 结构错误）不重试
                if (e !is java.net.SocketTimeoutException && e !is java.io.IOException) break
            }
        }

        val reason = lastReason ?: "AI 解析异常"
        lastFallbackReason = reason
        AiOutcome.TaskDrafts(fallbackToRawTask(input, reason))
    }

    /**
     * 事项管理工具定义（OpenAI function calling 兼容格式），
     * 轻量实现：不引 SDK，复用现有 HTTP 栈
     */
    private fun buildToolSpecs(): List<Map<String, Any?>> {
        fun tool(
            name: String,
            description: String,
            required: List<String>,
            props: Map<String, Pair<String, String>>
        ): Map<String, Any?> = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to description,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to props.mapValues { (_, spec) ->
                        mapOf("type" to spec.first, "description" to spec.second)
                    },
                    "required" to required
                )
            )
        )

        return listOf(
            tool(
                "add_task",
                "新增/记录一条生活事项或日程。用户想记录新事情时调用，一次调用只添加一条；多条事项请并行多次调用。",
                listOf("title", "is_calendar_event"),
                mapOf(
                    "title" to ("string" to "简练的书面语动宾短语，如「购买咖啡豆」，不要照抄口语原文"),
                    "is_calendar_event" to ("boolean" to "有明确日期时间的聚会、约见、出行、仪式等为 true；只有截止日的任务为 false"),
                    "start_time" to ("string" to "绝对开始时间，格式 YYYY-MM-DD HH:mm:ss；有明确日期必填，未说时刻默认当天 09:00:00，月日已过今年的推算为明年"),
                    "end_time" to ("string" to "绝对结束/截止时间，格式 YYYY-MM-DD HH:mm:ss，无则不传"),
                    "priority" to ("string" to "high/medium/low，纪念日、生日、体检、婚宴、家庭约定等重要生活事项为 high"),
                    "location" to ("string" to "地点，无则不传"),
                    "tag" to ("string" to "家庭/健康/个人/财务/纪念/社交 之一"),
                    "reminder_minutes_before" to ("integer" to "提前提醒的分钟数，不填由 App 按默认策略处理"),
                    "recurrence" to ("string" to "重复规则: daily=每天、weekly=每周、monthly=每月。用户说「每天/每周一/每月X号提醒我…」时传对应值，不重复则不传")
                )
            ),
            tool(
                "update_task",
                "修改一条已存在的事项（改时间、改标题、改地点、改优先级、改提醒、标记完成等）。用户说「把…改到…」时调用。",
                listOf("title_keyword"),
                mapOf(
                    "title_keyword" to ("string" to "用于匹配现有事项标题的关键词（1-4 个字）"),
                    "new_title" to ("string" to "新的标题，不改则不传"),
                    "new_start_time" to ("string" to "新的开始时间 YYYY-MM-DD HH:mm:ss，不改则不传"),
                    "new_end_time" to ("string" to "新的结束/截止时间 YYYY-MM-DD HH:mm:ss，不改则不传"),
                    "new_priority" to ("string" to "high/medium/low，不改则不传"),
                    "new_location" to ("string" to "新地点，不改则不传"),
                    "new_reminder_minutes_before" to ("integer" to "新的提前提醒分钟数，不改则不传"),
                    "new_recurrence" to ("string" to "新的重复规则: daily/weekly/monthly，不重复传 none，不改则不传"),
                    "mark_completed" to ("boolean" to "true 表示同时把该事项标记为已完成")
                )
            ),
            tool(
                "delete_task",
                "删除一条已存在的事项。",
                listOf("title_keyword"),
                mapOf("title_keyword" to ("string" to "用于匹配要删除事项标题的关键词"))
            ),
            tool(
                "complete_task",
                "把一条未完成的事项标记为完成。用户说「XX做完了/办完了」时调用。",
                listOf("title_keyword"),
                mapOf("title_keyword" to ("string" to "用于匹配事项标题的关键词"))
            ),
            tool(
                "query_tasks",
                "查询用户的事项清单。用户问「今天有什么事/这周有哪些待办/体检是哪天」时调用。",
                emptyList(),
                mapOf(
                    "status" to ("string" to "pending=未完成(默认)、completed=已完成、all=全部"),
                    "keyword" to ("string" to "按标题关键词过滤，可不传"),
                    "scope" to ("string" to "today=仅今天、upcoming=今天及以后(默认)、all=全部时间")
                )
            )
        )
    }

    /**
     * 清洗大模型输出中可能存在的 ```json ... ``` 标记，并截取最外层合法 JSON
     */
    internal fun cleanMarkdownJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        clean = clean.trim()

        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return clean.substring(firstBrace, lastBrace + 1)
        }
        return clean
    }

    internal fun parseDateStringToMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            dateTimeFormat.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 最近一次 processInput 的降级原因；null 表示 AI 正常返回
     */
    @Volatile
    var lastFallbackReason: String? = null
        private set

    /**
     * AI 不可用时的诚实兜底（方案B）：不做任何猜测式拆解，
     * 原文存为单条普通待办，用户可在草稿编辑面板核对后手动重试 AI。
     */
    internal fun fallbackToRawTask(input: String, reason: String): List<TaskEntity> {
        return listOf(TaskEntity(title = input.trim(), tag = "待办"))
    }
}
