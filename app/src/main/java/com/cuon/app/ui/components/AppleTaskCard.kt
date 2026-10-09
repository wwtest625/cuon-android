package com.cuon.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.formatSmartDeadline
import com.cuon.app.ui.theme.AppleBlue
import com.cuon.app.ui.theme.AppleRed
import com.cuon.app.ui.theme.PriorityHigh

/**
 * Apple 极简线条风格待办卡片 (Task Card)
 * 包含：微圆形打勾动效、弹力微缩放、划线褪色完成动效、极细线条边框
 */
@Composable
fun AppleTaskCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit = {},
    onTagClick: (String) -> Unit = {}
) {
    var isCheckedAnim by remember(task.isCompleted) { mutableStateOf(task.isCompleted) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isCheckedAnim) 0.55f else 1.0f,
        animationSpec = tween(300),
        label = "cardAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .clickable { onClick() }
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0x0A000000)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // 纯实心底色，防透视
        ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline) // 苹果极细轮廓线
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleCircleCheckbox(
                checked = isCheckedAnim,
                onCheckedChange = {
                    isCheckedAnim = !isCheckedAnim
                    onToggle()
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCheckedAnim) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (isCheckedAnim) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCheckedAnim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if ((task.endTime != null && !task.isCompleted) || task.tag.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.endTime != null && !task.isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppleRed.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, AppleRed.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "\u23f0 ${formatSmartDeadline(task.endTime)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = AppleRed,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        if (task.tag.isNotBlank()) {
                            Text(
                                text = "#${task.tag}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = AppleBlue,
                                modifier = Modifier.clickable { onTagClick(task.tag) }
                            )
                        }
                        if (task.reminderMinutesBefore != null && !task.isCompleted) {
                            val reminderLabel = if (task.reminderMinutesBefore == 0) "\u51c6\u65f6" else "\u63d0\u524d${task.reminderMinutesBefore}\u5206"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF34C759).copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, Color(0xFF34C759).copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "\ud83d\udd14 $reminderLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF34C759),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 右侧优先级指示:仅高优先级显示红拇指
            if (task.priority == "high" && !task.isCompleted) {
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = "高优先级",
                    tint = PriorityHigh,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Apple Reminders 风格圆形弹性复选框
 */
@Composable
fun AppleCircleCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )

    Box(
        modifier = Modifier
            .size(22.dp)
            .scale(checkScale)
            .clip(CircleShape)
            .clickable { onCheckedChange() }
            .then(
                if (checked) {
                    Modifier.background(AppleBlue)
                } else {
                    Modifier.border(BorderStroke(1.5.dp, Color(0xFFC7C7CC)), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
