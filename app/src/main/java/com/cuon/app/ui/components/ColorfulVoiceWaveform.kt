package com.cuon.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Apple 风格 Siri / 极光炫彩流体声波组件 (Fluid Aurora Voice Waveform)
 * 当用户按住语音输入按钮时呈现的多层渐变、相位奔涌正弦声波
 */
@Composable
fun ColorfulVoiceWaveform(
    modifier: Modifier = Modifier,
    isListening: Boolean = true,
    amplitudeMultiplier: Float = 1.0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceWaves")

    // 相位循环动画 (模拟波浪奔腾流淌)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // 振幅脉冲微起伏 (模拟人声拾音时的声浪律动)
    val dynamicAmp by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dynamicAmp"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val baseAmplitude = (height * 0.42f) * dynamicAmp * amplitudeMultiplier

        if (!isListening) return@Canvas

        // 1. 底层流体光晕 (Teal & Green 柔和底光)
        drawSineWave(
            width = width,
            centerY = centerY,
            amplitude = baseAmplitude * 0.45f,
            frequency = 1.8f,
            phase = phase * 0.8f,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF30B0C7).copy(alpha = 0.35f),
                    Color(0xFF34C759).copy(alpha = 0.45f),
                    Color(0xFF007AFF).copy(alpha = 0.35f)
                )
            ),
            strokeWidth = 3.5.dp.toPx()
        )

        // 2. 中层流动声波 (Apple Blue & Indigo & Purple)
        drawSineWave(
            width = width,
            centerY = centerY,
            amplitude = baseAmplitude * 0.75f,
            frequency = 2.4f,
            phase = phase * 1.2f + 1.2f,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF007AFF).copy(alpha = 0.85f),
                    Color(0xFF5856D6).copy(alpha = 0.95f),
                    Color(0xFFAF52DE).copy(alpha = 0.85f)
                )
            ),
            strokeWidth = 4.dp.toPx()
        )

        // 3. 顶层主爆发波浪 (Pink & Orange & Purple 绚烂主色)
        drawSineWave(
            width = width,
            centerY = centerY,
            amplitude = baseAmplitude * 0.95f,
            frequency = 3.0f,
            phase = phase * 1.5f + 2.4f,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFFF2D55),
                    Color(0xFFFF9500),
                    Color(0xFFAF52DE),
                    Color(0xFF007AFF)
                )
            ),
            strokeWidth = 3.dp.toPx()
        )
    }
}

/**
 * 绘制平滑的正弦波线，带有边缘渐隐遮罩（两端衰减至0，保持悬浮美感）
 */
private fun DrawScope.drawSineWave(
    width: Float,
    centerY: Float,
    amplitude: Float,
    frequency: Float,
    phase: Float,
    brush: Brush,
    strokeWidth: Float
) {
    val path = Path()
    val steps = 80
    val stepX = width / steps

    for (i in 0..steps) {
        val x = i * stepX
        // 汉宁窗 (Hanning Window) 使得两端平滑收束为 0，中间声浪饱满奔涌
        val window = 0.5f * (1f - kotlin.math.cos(2f * PI.toFloat() * (x / width)))
        val currentAmp = amplitude * window
        val y = centerY + sin((x / width) * 2f * PI.toFloat() * frequency + phase) * currentAmp

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        brush = brush,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
