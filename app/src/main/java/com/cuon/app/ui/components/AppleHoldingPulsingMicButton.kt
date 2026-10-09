package com.cuon.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cuon.app.ui.theme.AppleBlue
import com.cuon.app.ui.theme.AppleRed

/**
 * 麦克风按钮 (呼吸脉冲光环)
 * 按住开始讯飞语音听写(边说边出字);手指滑出按钮进入取消态,松开即取消;
 * 在按钮上松开则结束并送 AI。手势宿主常驻组合树,finally 兜底保证松开必收尾。
 */
@Composable
fun AppleHoldingPulsingMicButton(
    modifier: Modifier = Modifier,
    buttonSize: Dp = 42.dp,
    isProcessing: Boolean,
    isListening: Boolean = false,
    onPressStart: () -> Unit,
    onPressEnd: (send: Boolean) -> Unit,
    onSlideModeChanged: (cancelMode: Boolean) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isProcessing) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "buttonPressScale"
    )

    Box(
        modifier = modifier.size(buttonSize),
        contentAlignment = Alignment.Center
    ) {
        // 动态呼吸脉冲环
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(AppleBlue.copy(alpha = pulseAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(buttonSize - 4.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(if (isListening) AppleRed else AppleBlue)
                .pointerInput(isProcessing) {
                    if (isProcessing) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onPressStart()
                        var inside = true
                        val slop = viewConfiguration.touchSlop.toFloat()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val pos = change.position
                                val insideNow = pos.x >= -slop && pos.x <= size.width + slop &&
                                        pos.y >= -slop && pos.y <= size.height + slop
                                if (insideNow != inside) {
                                    inside = insideNow
                                    onSlideModeChanged(!inside)
                                }
                                change.consume()
                            }
                        } finally {
                            onPressEnd(inside)
                            if (!inside) onSlideModeChanged(false)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "结束语音输入" else "语音输入",
                tint = Color.White,
                modifier = Modifier.size((buttonSize * 0.45f).coerceAtLeast(18.dp))
            )
        }
    }
}
