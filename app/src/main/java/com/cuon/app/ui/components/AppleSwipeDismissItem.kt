package com.cuon.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cuon.app.ui.theme.AppleRed

/**
 * Apple 风格左滑删除容器 (Swipe-to-Dismiss) + 灭霸打响指粒子消散特效
 * 彻底消除文字重叠：卡片本身 100% 实心白色不透明；触发删除时如灭霸打响指般化为飞沙烟尘消散
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleSwipeDismissItem(
    itemId: Long = 0L,
    generation: Int = 0,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSnapping by remember(itemId, generation) { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                isSnapping = true
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.38f }
    )

    // 解决 P1 红色残影：撤销恢复 (generation 变更) 或组件重建时，强制复位滑动状态与消散动效
    LaunchedEffect(itemId, generation) {
        isSnapping = false
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    ThanosSnapDisintegration(
        isDisintegrating = isSnapping,
        onDisintegrated = onDismiss
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                val iconScale by animateFloatAsState(
                    targetValue = if (isSwiping) 1.25f else 0.95f,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "deleteIconScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleRed)
                        .padding(end = 22.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(iconScale)
                    )
                }
            }
        ) {
            // 卡片内容容器：强制保持实心不透明，避免半透明透光！
            content()
        }
    }
}
