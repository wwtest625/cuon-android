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
import java.util.regex.Pattern

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
    private val apiKey: String = "sk-fm2UIcm8dhC5eKnbSMZpuhQcuqWaTZk59uaObkMXNww4hBwh",
    private val apiBaseUrl: String = "https://apihub.agnes-ai.cn/v1",
    private val modelName: String = "agnes-2.5-flash"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

        try {
            val systemPrompt = """
                你是一个专业的高效个人日程与待办拆解专家。
                当前系统基准时间是: $currentTimeString。
                请将用户输入的自然语言，提取并拆解为原子化的清单项。
                请根据当前基准时间，精准计算出所有相对时间的绝对日期与时间（格式统一为：YYYY-MM-DD HH:mm:ss）。
                输出必须是严格的 JSON 格式：
                {
                  "items": [
                    {
                      "title": "动宾短语描述事项（简练有力）",
                      "isCalendarEvent": true/false (若是特定时间段的会议、碰头、出行等日程为true；若是只需在截止日前完成的待办事项为false),
                      "startTime": "YYYY-MM-DD HH:mm:ss或null",
                      "endTime": "YYYY-MM-DD HH:mm:ss或null",
                      "priority": "high/medium/low",
                      "location": "地点，无则留空",
                      "tag": "工作/个人/财务/会议"
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

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext parseLocally(input)
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

            if (resultList.isNotEmpty()) resultList else parseLocally(input)
        } catch (e: Exception) {
            e.printStackTrace()
            parseLocally(input)
        }
    }

    /**
     * 清洗大模型输出中可能存在的 ```json ... ``` 标记，并截取最外层合法 JSON
     */
    private fun cleanMarkdownJson(raw: String): String {
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

    private fun parseDateStringToMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            dateTimeFormat.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 本地快速规则解析器（离线或模型异常时毫秒级兜底）
     */
    private fun parseLocally(input: String): List<TaskEntity> {
        val list = mutableListOf<TaskEntity>()
        val parts = input.split(Pattern.compile("[；;，,。\\n]+")).filter { it.isNotBlank() }

        parts.forEach { part ->
            val isCalendar = part.contains("开会") || part.contains("碰头") || part.contains("面试") || part.contains("聊")
            val priority = if (part.contains("紧急") || part.contains("必须") || part.contains("立即")) "high" else "medium"
            list.add(
                TaskEntity(
                    title = part.trim(),
                    isCalendarEvent = isCalendar,
                    priority = priority,
                    tag = if (isCalendar) "日程" else "待办",
                    startTime = System.currentTimeMillis() + if (isCalendar) 3600_000 else 0
                )
            )
        }

        if (list.isEmpty() && input.isNotBlank()) {
            list.add(TaskEntity(title = input.trim()))
        }
        return list
    }
}
