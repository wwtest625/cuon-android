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
     * 将输入的自然语言（语音或文本）送入大模型拆解为日程和任务实体
     */
    suspend fun parseTextToTasks(input: String): List<TaskEntity> = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext emptyList()

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
            输出必须是严格的 JSON 格式：
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

        val requestJson = mapOf(
            "model" to modelName,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to input)
            ),
            "response_format" to mapOf("type" to "json_object")
        )

        val requestBodyString = gson.toJson(requestJson)
        val request = Request.Builder()
            .url("$apiBaseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .build()

        // 瞬时故障（超时/网络抖动/5xx/空返回）自动重试 1 次，避免偶发慢响应误入兜底
        var lastReason: String? = null

        for (attempt in 1..2) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    if (response.code >= 500 && attempt < 2) {
                        lastReason = "AI 服务异常 (HTTP ${response.code})"
                        continue
                    }
                    val reason = "AI 服务异常 (HTTP ${response.code})"
                    lastFallbackReason = reason
                    return@withContext fallbackToRawTask(input, reason)
                }

                // 从 chat.completion 结果中解构 content 字段
                val rootJson = gson.fromJson(responseBody, Map::class.java)
                val choices = rootJson["choices"] as? List<*>
                val firstChoice = choices?.firstOrNull() as? Map<*, *>
                val message = firstChoice?.get("message") as? Map<*, *>
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

                if (resultList.isNotEmpty()) return@withContext resultList
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
        fallbackToRawTask(input, reason)
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
     * 最近一次 parseTextToTasks 的降级原因；null 表示 AI 正常返回
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
