package com.cuon.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.cuon.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 高德 Web 服务 POI 搜索（v3/place/text）
 * 用于地点快捷选择：关键词 → POI 列表 → 回填 location 字符串
 */
class AmapPoiService(
    private val apiKey: String = BuildConfig.AMAP_REST_KEY
) {

    /** key 未配置或非法（服务端 INVALID_USER_KEY） */
    class AmapKeyInvalidException(message: String) : Exception(message)

    data class PoiResult(
        @SerializedName("id") val id: String = "",
        @SerializedName("name") val name: String = "",
        // 容错：部分 POI 类型的 address 会返回空数组 [] 而非字符串
        @SerializedName("address") val addressRaw: Any? = null,
        @SerializedName("pname") val pname: String? = null,
        @SerializedName("cityname") val cityname: String? = null,
        @SerializedName("district") val district: String? = null
    ) {
        val address: String get() = (addressRaw as? String)?.trim() ?: ""

        /**
         * 拼接回填文本：省市区（相邻去重）+ 详细地址/POI名
         * 例："浙江省杭州市西湖区文三路 XX 大厦"
         */
        fun toDisplayAddress(): String {
            val region = listOf(pname, cityname, district)
                .filter { !it.isNullOrBlank() }
                .distinct()
                .joinToString("")
            val detail = if (address.isNotBlank() && address != name) address else name
            return (region + detail).trim()
        }
    }

    private data class PoiResponse(
        @SerializedName("status") val status: String = "0",
        @SerializedName("info") val info: String = "",
        @SerializedName("pois") val pois: List<PoiResult>? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 关键词搜索 POI，返回最多 10 条
     * @throws AmapKeyInvalidException key 未配置或被服务端拒绝
     * @throws IOException 网络不可用/超时
     */
    suspend fun searchPois(keywords: String): List<PoiResult> = withContext(Dispatchers.IO) {
        if (keywords.isBlank()) return@withContext emptyList()
        if (apiKey.isBlank() || apiKey.length < 32) {
            throw AmapKeyInvalidException("AMAP_REST_KEY 未配置")
        }

        val url = "https://restapi.amap.com/v3/place/text" +
                "?key=${URLEncoder.encode(apiKey, "UTF-8")}" +
                "&keywords=${URLEncoder.encode(keywords.trim(), "UTF-8")}" +
                "&offset=10&page=1&extensions=base"

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""

        val parsed = try {
            gson.fromJson(body, PoiResponse::class.java)
        } catch (e: Exception) {
            throw IOException("POI 响应解析失败")
        }

        if (parsed.status == "0") {
            if (parsed.info.contains("INVALID_USER_KEY", ignoreCase = true) ||
                parsed.info.contains("USER_DAILY_QUERY_OVER_LIMIT", ignoreCase = true)
            ) {
                throw AmapKeyInvalidException(parsed.info)
            }
            throw IOException(parsed.info.ifBlank { "POI 查询失败" })
        }

        parsed.pois ?: emptyList()
    }
}
