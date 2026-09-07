package com.cuon.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.cuon.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 讯飞 语音听写(流式版) WebAPI 客户端 (wss://iat-api.xfyun.cn/v2/iat)
 *
 * - 边说边出字:onPartial 实时回调 (wpgs 动态修正)
 * - 结束方式:手动 stopListening() 或静音超过 vad_eos(默认10s)由服务端断句
 * - 需要在 local.properties 配置 XFYUN_APP_ID / XFYUN_API_KEY / XFYUN_API_SECRET
 */
class XfyunIatManager(
    @Suppress("unused") private val context: Context,
    private val appId: String = BuildConfig.XFYUN_APP_ID,
    private val apiKey: String = BuildConfig.XFYUN_API_KEY,
    private val apiSecret: String = BuildConfig.XFYUN_API_SECRET
) {

    var onPartial: ((String) -> Unit)? = null
    var onFinal: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isListening: Boolean get() = recordJob?.isActive == true

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket 长连接不设读超时
        .build()

    private var ws: WebSocket? = null
    private var recordJob: Job? = null

    // 用户取消:本次会话的任何回调(最终结果/连接错误)都不再上抛
    private var sessionCancelled = false

    // wpgs 动态修正:sn → 该序号的分片文本,最终文本 = 各分片按序拼接
    private val snSegments = LinkedHashMap<Int, String>()
    private var sessionJob: Job? = null

    fun startListening(onMainThread: (Runnable) -> Unit) {
        if (isListening) return
        if (appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            onError?.invoke("讯飞凭据未配置(需 XFYUN_APP_ID 等)")
            return
        }

        resetSession()
        sessionCancelled = false

        val request = Request.Builder().url(buildAuthUrl()).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                startRecordingLoop(webSocket, onMainThread)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text, onMainThread)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                recordJob?.cancel()
                if (!sessionCancelled) {
                    onMainThread(Runnable { onError?.invoke("语音服务连接失败: ${t.message}") })
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
        })
    }

    /** 手动结束:发送最后一帧,等服务端返回最终文本后回调 onFinal */
    fun stopListening() {
        ws?.let { socket ->
            val endFrame = gson.toJson(
                JsonObject().apply {
                    add("data", JsonObject().apply {
                        addProperty("status", 2)
                        addProperty("format", "audio/L16;rate=16000")
                        addProperty("encoding", "raw")
                        addProperty("audio", "")
                    })
                }
            )
            socket.send(endFrame)
        }
        recordJob?.cancel()
    }

    /** 用户取消:丢弃本次录音,不回调 onFinal */
    fun cancelListening() {
        sessionCancelled = true
        recordJob?.cancel()
        resetSession()
        ws?.close(1000, "cancelled")
        ws = null
    }

    fun release() {
        recordJob?.cancel()
        sessionJob?.cancel()
        ws?.close(1000, "release")
        ws = null
    }

    // ── 内部 ────────────────────────────────────────────────

    /** 鉴权 URL:hmac-sha256 签名,见讯飞 WebAPI 文档 */
    private fun buildAuthUrl(): String {
        val host = "iat-api.xfyun.cn"
        val path = "/v2/iat"
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        val signatureOrigin = "host: $host\ndate: $date\nGET $path HTTP/1.1"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(), "HmacSHA256"))
        val signature = Base64.encodeToString(mac.doFinal(signatureOrigin.toByteArray()), Base64.NO_WRAP)

        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(authorizationOrigin.toByteArray(), Base64.NO_WRAP)

        return "wss://$host$path" +
                "?authorization=${URLEncoder.encode(authorization, "UTF-8")}" +
                "&date=${URLEncoder.encode(date, "UTF-8")}" +
                "&host=$host"
    }

    @SuppressLint("MissingPermission") // 调用方负责运行时权限
    private fun startRecordingLoop(webSocket: WebSocket, onMainThread: (Runnable) -> Unit) {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            onMainThread(Runnable { onError?.invoke("无法初始化麦克风") })
            return
        }
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onMainThread(Runnable { onError?.invoke("麦克风初始化失败(检查权限)") })
            return
        }

        // 首帧:携带 common/business 配置
        val firstFrame = gson.toJson(
            JsonObject().apply {
                add("common", JsonObject().apply { addProperty("app_id", appId) })
                add("business", JsonObject().apply {
                    addProperty("language", "zh_cn")
                    addProperty("domain", "iat")
                    addProperty("accent", "mandarin")
                    addProperty("vad_eos", 10000)      // 静音10s服务端自动断句
                    addProperty("dwa", "wpgs")         // 动态修正
                })
                add("data", JsonObject().apply {
                    addProperty("status", 0)
                    addProperty("format", "audio/L16;rate=16000")
                    addProperty("encoding", "raw")
                    addProperty("audio", "")
                })
            }
        )
        webSocket.send(firstFrame)

        recordJob = CoroutineScope(Dispatchers.IO).launch {
            val buf = ShortArray(1280) // 40ms @16k
            try {
                audioRecord.startRecording()
                while (isActive) {
                    val n = audioRecord.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    val pcm = ByteArray(n * 2)
                    for (i in 0 until n) {
                        pcm[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                        pcm[i * 2 + 1] = (buf[i].toInt() shr 8).toByte()
                    }
                    val frame = gson.toJson(
                        JsonObject().apply {
                            add("data", JsonObject().apply {
                                addProperty("status", 1)
                                addProperty("format", "audio/L16;rate=16000")
                                addProperty("encoding", "raw")
                                addProperty("audio", Base64.encodeToString(pcm, Base64.NO_WRAP))
                            })
                        }
                    )
                    if (!webSocket.send(frame)) break
                }
            } catch (e: Exception) {
                onMainThread(Runnable { onError?.invoke("录音异常: ${e.message}") })
            } finally {
                try { audioRecord.stop() } catch (_: Exception) {}
                audioRecord.release()
            }
        }
    }

    private fun handleServerMessage(text: String, onMainThread: (Runnable) -> Unit) {
        if (sessionCancelled) return
        val root = gson.fromJson(text, JsonObject::class.java) ?: return
        val code = root.get("code")?.asInt ?: -1
        if (code != 0) {
            recordJob?.cancel()
            val msg = mapOf(
                10105 to "讯飞 APPID 不合法",
                10106 to "讯飞参数非法",
                10110 to "讯飞引擎会话已满",
                11200 to "讯飞授权不足(日配额已用完)",
                11201 to "讯飞日流控超限"
            )[code] ?: "讯飞服务错误($code)"
            onMainThread(Runnable { onError?.invoke(msg) })
            return
        }

        val data = root.getAsJsonObject("data") ?: return
        val result = data.getAsJsonObject("result") ?: run {
            // status=2 且无 result:空结果,视为一次会话结束
            if (data.get("status")?.asInt == 2) finishSession(onMainThread)
            return
        }

        // 解析 ws:[{cw:[{w}]}]
        val words = StringBuilder()
        result.getAsJsonArray("ws")?.forEach { wItem ->
            (wItem as? JsonObject)?.getAsJsonArray("cw")?.forEach { cItem ->
                (cItem as? JsonObject)?.get("w")?.asString?.let { words.append(it) }
            }
        }
        val pgs = result.get("pgs")?.asString ?: "apd"
        val sn = result.get("sn")?.asInt ?: 0
        if (pgs == "rpl") {
            // 替换区间 [rg0, rg1] 的旧分片
            val rg = result.getAsJsonArray("rg")
            if (rg != null && rg.size() == 2) {
                for (i in rg[0].asInt..rg[1].asInt) snSegments.remove(i)
            }
        }
        if (words.isNotBlank()) snSegments[sn] = words.toString()

        val currentText = snSegments.values.joinToString("")
        onMainThread(Runnable { onPartial?.invoke(currentText) })

        if (data.get("status")?.asInt == 2) {
            finishSession(onMainThread)
        }
    }

    /** 一句话识别完成:回调最终文本并关闭会话 */
    private fun finishSession(onMainThread: (Runnable) -> Unit) {
        recordJob?.cancel()
        val finalText = snSegments.values.joinToString("")
        resetSession()
        ws?.close(1000, "done")
        ws = null
        onMainThread(Runnable { onFinal?.invoke(finalText.trim()) })
    }

    private fun resetSession() {
        snSegments.clear()
    }
}
