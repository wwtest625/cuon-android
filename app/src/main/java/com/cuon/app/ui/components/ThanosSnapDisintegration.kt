package com.cuon.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cuon.app.ui.theme.ThanosDustAsh
import com.cuon.app.ui.theme.ThanosDustEmber
import com.cuon.app.ui.theme.ThanosDustGold
import kotlin.math.sin
import kotlin.random.Random

/**
 * 单个灭霸灰烬微粒子模型
 */
data class DustParticle(
    val normX: Float,        // 初始相对 X (0f..1f)
    val normY: Float,        // 初始相对 Y (0f..1f)
    val size: Float,         // 粒子像素大小 (2.5f..5.5f)
    val color: Color,        // 粒子颜色 (灰烬灰、火花金、微橙余烬)
    val velX: Float,         // 水平飘散速度 (灭霸风向向右吹动)
    val velY: Float,         // 垂直飘散速度 (向上/向下扩散)
    val rotationSpeed: Float,// 自旋速度
    val startDelay: Float    // 错峰消散延迟 (从左到右逐步化灰)
)

/**
 * 灭霸打响指粒子风化消散特效容器 (Thanos Snap Disintegration Container)
 * 当触发删除时，卡片迅速化为细沙烟尘随风消散
 */
@Composable
fun ThanosSnapDisintegration(
    modifier: Modifier = Modifier,
    isDisintegrating: Boolean,
    onDisintegrated: () -> Unit,
    content: @Composable () -> Unit
) {
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    // 灭霸消散进度动画 (0f -> 1f)
    val progress = remember { Animatable(0f) }

    // 生成灭霸灰烬粒子集群 (120 个微小沙粒)
    val particles = remember(itemSize) {
        if (itemSize.width == 0 || itemSize.height == 0) emptyList()
        else {
            val random = Random(42)
            List(120) {
                val normX = random.nextFloat()
                val normY = random.nextFloat()
                val colorPick = when (random.nextInt(4)) {
                    0 -> ThanosDustGold.copy(alpha = 0.9f)
                    1 -> ThanosDustEmber.copy(alpha = 0.85f)
                    else -> ThanosDustAsh.copy(alpha = 0.8f)
                }
                DustParticle(
                    normX = normX,
                    normY = normY,
                    size = 2.5f + random.nextFloat() * 3.5f,
                    color = colorPick,
                    velX = 180f + random.nextFloat() * 260f,   // 向右强烈风力
                    velY = -80f + random.nextFloat() * 160f,   // 向上轻扬微扰
                    rotationSpeed = -180f + random.nextFloat() * 360f,
                    startDelay = normX * 0.45f // 从左向右如同波浪般瓦解化灰
                )
            }
        }
    }

    LaunchedEffect(isDisintegrating) {
        if (isDisintegrating) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
            onDisintegrated()
        }
    }

    Box(
        modifier = modifier.onSizeChanged { itemSize = it }
    ) {
        // 卡片内容：随着消散进度迅速溶解变淡
        val contentAlpha = if (isDisintegrating) {
            (1f - progress.value * 2.2f).coerceIn(0f, 1f)
        } else {
            1f
        }

        Box(modifier = Modifier.alpha(contentAlpha)) {
            content()
        }

        // 灭霸烟尘粒子绘制层
        if (isDisintegrating && itemSize.width > 0 && itemSize.height > 0) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val p = progress.value

                particles.forEach { particle ->
                    // 根据延迟系数计算该粒子的局部消散生命周期 (0..1)
                    if (p >= particle.startDelay) {
                        val localP = ((p - particle.startDelay) / (1f - particle.startDelay)).coerceIn(0f, 1f)
                        val currentAlpha = (1f - localP).coerceIn(0f, 1f)

                        // 飘散位移：向右狂风吹拂 + 正弦轻微漂浮浪涌
                        val posX = (particle.normX * size.width) + particle.velX * localP + sin(localP * 5f) * 16f
                        val posY = (particle.normY * size.height) + particle.velY * localP - (localP * localP * 40f)

                        val currentSize = particle.size * (1f - localP * 0.4f)

                        if (currentAlpha > 0.02f) {
                            rotate(
                                degrees = particle.rotationSpeed * localP,
                                pivot = Offset(posX, posY)
                            ) {
                                drawCircle(
                                    color = particle.color.copy(alpha = currentAlpha * particle.color.alpha),
                                    radius = currentSize,
                                    center = Offset(posX, posY)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
