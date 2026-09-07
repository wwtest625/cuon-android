package com.cuon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import java.text.SimpleDateFormat
import java.util.*

private val AppleBlue = Color(0xFF007AFF)
private val AppleRed = Color(0xFFFF3B30)
private val AppleGreen = Color(0xFF34C759)
private val AppleOrange = Color(0xFFFF9500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDraftPreviewBottomSheet(
    draftItems: List<TaskEntity>,
    onDismiss: () -> Unit,
    onConfirmAll: (List<TaskEntity>) -> Unit,
    onItemUpdate: (Int, TaskEntity) -> Unit,
    onItemRemove: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 单项编辑弹窗状态
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // 顶栏提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "✨ AI 拆解就绪 (${draftItems.size}项)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "请核对时间、地点与提醒，确认后记入生活事项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "取消",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 拆解出的事项列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(draftItems) { index, item ->
                    AiDraftItemCard(
                        item = item,
                        onEdit = { editingIndex = index },
                        onDelete = { onItemRemove(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("放弃全部")
                }

                Button(
                    onClick = { onConfirmAll(draftItems) },
                    enabled = draftItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.8f)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("确认记入 (${draftItems.size})", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 若用户点击了某一项编辑，弹出全量编辑 Sheet
    editingIndex?.let { idx ->
        if (idx in draftItems.indices) {
            TaskEditBottomSheet(
                task = draftItems[idx],
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    onItemUpdate(idx, updated)
                    editingIndex = null
                },
                onDelete = {
                    onItemRemove(idx)
                    editingIndex = null
                }
            )
        }
    }
}

@Composable
private fun AiDraftItemCard(
    item: TaskEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCalendar = item.isCalendarEvent
    val typeBg = if (isCalendar) AppleBlue.copy(alpha = 0.1f) else AppleOrange.copy(alpha = 0.1f)
    val typeColor = if (isCalendar) AppleBlue else AppleOrange
    val typeText = if (isCalendar) "📅 日程" else "📝 待办"

    val timeText = remember(item.startTime, item.endTime) {
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.CHINESE)
        val timeOnlySdf = SimpleDateFormat("HH:mm", Locale.CHINESE)
        when {
            item.startTime != null && item.endTime != null -> {
                "${sdf.format(Date(item.startTime))} - ${timeOnlySdf.format(Date(item.endTime))}"
            }
            item.startTime != null -> sdf.format(Date(item.startTime))
            item.endTime != null -> "截止: ${sdf.format(Date(item.endTime))}"
            else -> "无固定时间"
        }
    }

    val reminderText = when (item.reminderMinutesBefore) {
        null -> null
        0 -> "🔔 准时提醒"
        15 -> "🔔 提前15分钟"
        30 -> "🔔 提前30分钟"
        else -> "🔔 提前${item.reminderMinutesBefore}分钟"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = typeText, fontSize = 11.sp, color = typeColor, fontWeight = FontWeight.SemiBold)
                    }

                    if (item.tag.isNotBlank()) {
                        Text(
                            text = "#${item.tag}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (reminderText != null) {
                        Text(
                            text = reminderText,
                            fontSize = 11.sp,
                            color = AppleGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.location.isNotBlank()) {
                        Text(
                            text = "· 📍${item.location}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 右侧操作：编辑与剔除
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(16.dp),
                        tint = AppleBlue
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "剔除",
                        modifier = Modifier.size(16.dp),
                        tint = AppleRed
                    )
                }
            }
        }
    }
}
