package com.cuon.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.formatSmartDateTime
import com.cuon.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Apple 风格微光呼吸骨架屏 Shimmer 笔刷
 */
@Composable
fun shimmerBrush(targetValue: Float = 1000f): Brush {
    val shimmerColors = listOf(
        Color(0xFFE5E5EA).copy(alpha = 0.45f),
        Color(0xFFF2F2F7).copy(alpha = 0.95f),
        Color(0xFFE5E5EA).copy(alpha = 0.45f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

/**
 * 0 延迟即时呈现的 Apple 极简骨架卡片 (Skeleton Ghost Card)
 */
@Composable
fun SkeletonGhostCard(
    modifier: Modifier = Modifier,
    isCalendar: Boolean = true
) {
    val brush = shimmerBrush()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = Color(0x0A000000)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧指示条 / 圆框骨架
            if (isCalendar) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 标题骨架条
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 时间 / 标签副信息骨架条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )
                }
            }

            // 右侧“AI 思考中”胶囊
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AppleBlue.copy(alpha = 0.08f),
                border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "推算中...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AppleBlue
                    )
                }
            }
        }
    }
}

/**
 * 流式打字机填字卡片 (Streaming Typewriter Card)
 * 骨架变实后，文字以 28ms 的节奏逐字打印吐出，带微光标
 */
@Composable
fun StreamingTypewriterCard(
    item: TaskEntity,
    onFinishTyping: () -> Unit
) {
    val fullTitle = item.title
    var displayedLength by remember { mutableStateOf(0) }
    var isTypingComplete by remember { mutableStateOf(false) }

    // 光标闪烁
    val cursorTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    // 逐字吐出协程
    LaunchedEffect(fullTitle) {
        displayedLength = 0
        for (i in 1..fullTitle.length) {
            displayedLength = i
            delay(26) // 极速流畅的打字速度
        }
        delay(180)
        isTypingComplete = true
        onFinishTyping()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, RoundedCornerShape(16.dp), spotColor = Color(0x12007AFF)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            1.dp,
            if (!isTypingComplete) AppleBlue.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧指示器
            if (item.isCalendarEvent) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(AppleBlue)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(AppleBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AppleBlue)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 正在流式打字的主标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fullTitle.take(displayedLength),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isTypingComplete) {
                        Text(
                            text = "▍",
                            color = AppleBlue.copy(alpha = cursorAlpha),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 时间与地点 (随流式完成逐步显现)
                if (displayedLength > (fullTitle.length * 0.4f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isCalendarEvent) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = AppleBlue
                            )
                            Text(
                                text = formatSmartDateTime(item.startTime, item.endTime),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            if (item.location.isNotBlank()) {
                                Text(text = "·", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = item.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            if (item.tag.isNotBlank()) {
                                Text(
                                    text = "#${item.tag}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = AppleBlue
                                )
                            }
                        }
                    }
                }
            }

            // 右侧流式状态徽章
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (!isTypingComplete) AppleBlue.copy(alpha = 0.12f) else Color.Transparent,
                border = if (!isTypingComplete) BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.3f)) else null
            ) {
                Text(
                    text = if (!isTypingComplete) "✨ 填字中" else "已就绪",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    ),
                    color = AppleBlue,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
