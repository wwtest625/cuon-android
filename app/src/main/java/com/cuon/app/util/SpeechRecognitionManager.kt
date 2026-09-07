package com.cuon.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统级语音听写管理器 (基于 Android 框架 SpeechRecognizer API)
 *
 * 直连系统默认识别服务（荣耀 MagicVoice / 小米小爱 / 讯飞等厂商引擎），
 * 无需谷歌 GMS、无需第三方 SDK 与账号、App 本身不申请录音权限（音频由系统服务采集）。
 *
 * 交互模型（微信式）：start() 开始聆听 → 松开/finish() 结束并回调识别文本。
 * onRmsChanged 提供真实音量，供 UI 波浪绑定；onPartialResults 提供实时上屏文字。
 */
class SpeechRecognitionManager(private val context: Context) {

    enum class State { IDLE, LISTENING, ERROR }

    data class SpeechUiState(
        val state: State = State.IDLE,
        val rmsDb: Float = 0f,          // 实时音量 (0..10 左右)
        val partialText: String = "",   // 实时识别上屏
        val errorMessage: String = ""
    )

    private val _uiState = MutableStateFlow(SpeechUiState())
    val uiState: StateFlow<SpeechUiState> = _uiState.asStateFlow()

    /** 识别结束回调（成功时返回文本，失败时返回 null 并带 errorMessage） */
    var onFinalResult: ((String?) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null

    /** 系统是否存在可用识别引擎 */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (!isAvailable()) return null
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(internalListener)
            }
        }
        return recognizer
    }

    fun startListening() {
        if (_uiState.value.state == State.LISTENING) return
        val sr = ensureRecognizer()
        if (sr == null) {
            _uiState.value = SpeechUiState(state = State.ERROR, errorMessage = "未检测到系统语音引擎")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _uiState.value = SpeechUiState(state = State.LISTENING)
        sr.startListening(intent)
    }

    /** 结束聆听并等待最终结果 */
    fun finishListening() {
        if (_uiState.value.state != State.LISTENING) return
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        _uiState.value = SpeechUiState()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _uiState.value = SpeechUiState()
    }

    private val internalListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _uiState.value = _uiState.value.copy(state = State.LISTENING, partialText = "", rmsDb = 0f)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (_uiState.value.state == State.LISTENING) {
                _uiState.value = _uiState.value.copy(rmsDb = rmsdB)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再试一次"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没检测到说话"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "语音权限未授予"
                SpeechRecognizer.ERROR_NETWORK -> "识别服务网络异常"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别服务网络超时"
                SpeechRecognizer.ERROR_CLIENT -> "语音客户端异常"
                SpeechRecognizer.ERROR_SERVER -> "识别服务端异常"
                else -> "语音识别出错 ($error)"
            }
            _uiState.value = SpeechUiState(state = State.ERROR, errorMessage = msg)
            onFinalResult?.invoke(null)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            _uiState.value = SpeechUiState()
            onFinalResult?.invoke(text?.takeIf { it.isNotBlank() })
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (!partial.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(partialText = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
