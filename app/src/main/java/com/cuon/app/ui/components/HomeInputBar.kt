package com.cuon.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cuon.app.ui.theme.AppleBlue
import com.cuon.app.ui.theme.AppleRed
import com.cuon.app.util.XfyunIatManager

/**
 * 首页底部常驻输入区
 * - 语音模式（大麦克风居中）为主，按住说话，滑动可取消
 * - 文字模式（键盘图标切换）为 TextField
 * - 说话过程中悬浮「聆听面板」实时出字 + 彩色波形
 *
 * 讯飞听写管理器的生命周期完全内聚在此，避免 HomeScreen 主体臃肿。
 *
 * @param onTextSubmit 文字提交回调（输入非空）
 * @param onVoiceResult 语音最终识别结果回调
 * @param onVoiceError 语音错误提示回调
 * @param isProcessingAi AI 是否正在处理（控制按钮禁用与转圈）
 */
@Composable
fun HomeInputBar(
    onTextSubmit: (String) -> Unit,
    onVoiceResult: (String) -> Unit,
    onVoiceError: (String) -> Unit,
    isProcessingAi: Boolean
) {
    val context = LocalContext.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    // 讯飞流式听写管理器：随本 Composable 生命周期创建与释放
    val iatManager = remember { XfyunIatManager(context) }

    var isVoiceListening by remember { mutableStateOf(false) }
    var voicePartial by remember { mutableStateOf("") }
    var voiceCancelMode by remember { mutableStateOf(false) } // 手指滑出麦克风,松开即取消
    var voicePressAt by remember { mutableStateOf(0L) }
    var isVoiceMode by remember { mutableStateOf(true) }      // true=语音(大麦克风) false=文字
    var textInput by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            iatManager.startListening { mainHandler.post(it) }
            isVoiceListening = true
        } else {
            Toast.makeText(context, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        iatManager.onPartial = { voicePartial = it }
        iatManager.onFinal = { text ->
            isVoiceListening = false
            voicePartial = ""
            if (text.isNotBlank()) onVoiceResult(text)
            else Toast.makeText(context, "未检测到语音内容", Toast.LENGTH_SHORT).show()
        }
        iatManager.onError = { msg ->
            isVoiceListening = false
            onVoiceError(msg)
        }
    }

    DisposableEffect(Unit) {
        onDispose { iatManager.release() }
    }

    fun toggleVoice() {
        if (isVoiceListening) {
            iatManager.stopListening()
            isVoiceListening = false
        } else {
            voicePartial = ""
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                iatManager.startListening { mainHandler.post(it) }
                isVoiceListening = true
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val onMicPressStart = {
        voiceCancelMode = false
        voicePressAt = System.currentTimeMillis()
        if (!isVoiceListening) toggleVoice()
        Unit
    }
    val onMicSlideModeChanged = { cancelMode: Boolean ->
        voiceCancelMode = cancelMode
        Unit
    }
    val onMicPressEnd = { send: Boolean ->
        if (isVoiceListening) {
            val tooShort = System.currentTimeMillis() - voicePressAt < 500
            if (send && !tooShort) {
                iatManager.stopListening()
            } else {
                iatManager.cancelListening()
                if (tooShort) {
                    Toast.makeText(context, "说话时间太短", Toast.LENGTH_SHORT).show()
                }
            }
            isVoiceListening = false
            voicePartial = ""
            voiceCancelMode = false
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 6.dp)
    ) {
        // 聆听面板：按住说话时悬浮在胶囊上方，实时出字
        val panelState = remember { MutableTransitionState(false) }
        panelState.targetState = isVoiceListening
        AnimatedVisibility(visibleState = panelState) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (voiceCancelMode) "松开手指，取消发送" else voicePartial.ifBlank { "正在聆听，请说话..." },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (voiceCancelMode) MaterialTheme.colorScheme.onSurfaceVariant else AppleBlue,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!voiceCancelMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                onClick = {
                                    iatManager.cancelListening()
                                    isVoiceListening = false
                                    voicePartial = ""
                                }
                            ) {
                                Text(
                                    text = "取消",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ColorfulVoiceWaveform(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                        isListening = true,
                        amplitudeMultiplier = 0.8f
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = Color(0x14000000)
                ),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
        ) {
            if (!isVoiceMode) {
                // ⌨️ 文字模式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                text = "记录生活安排、家庭日程...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                    )

                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onTextSubmit(textInput)
                                textInput = ""
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppleBlue)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "提交",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (isProcessingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(26.dp)
                                .padding(4.dp),
                            strokeWidth = 2.dp,
                            color = AppleBlue
                        )
                    }

                    // 切回语音模式
                    IconButton(onClick = { isVoiceMode = true }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "语音输入",
                            tint = AppleBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                // 🎙 语音模式:大麦克风居中,键盘图标右下角
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    AppleHoldingPulsingMicButton(
                        modifier = Modifier.align(Alignment.Center),
                        buttonSize = 56.dp,
                        isProcessing = isProcessingAi,
                        isListening = isVoiceListening,
                        onPressStart = onMicPressStart,
                        onPressEnd = onMicPressEnd,
                        onSlideModeChanged = onMicSlideModeChanged
                    )
                    IconButton(
                        onClick = { isVoiceMode = false },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 14.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "键盘输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
