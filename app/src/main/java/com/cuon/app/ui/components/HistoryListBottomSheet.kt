package com.cuon.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.formatHistoryGroupLabel
import com.cuon.app.ui.historyAnchorMillis
import com.cuon.app.ui.CuonViewModel
import java.util.Calendar

/**
 * 历史记录底部弹层：全部已完成事项按完成（归属）日期倒序分组，提供日历之外的浏览视角。
 * 底部附「最近删除」回收站分区：软删除事项可恢复，保留 30 天后自动彻底清理。
 *
 * 分组规则：今天 / 昨天 / M月d日 EEE（跨年带年份），
 * 依赖入参已按历史归属时间倒序排列，逐项连续归组即可保持时间顺序。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListBottomSheet(
    completedTasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onEditTask: (TaskEntity) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    trashedTasks: List<TaskEntity> = emptyList(),
    onRestoreTask: (TaskEntity) -> Unit = {},
    onDeleteForever: (TaskEntity) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 连续归组：入参倒序 ⇒ 组间天然按时间从新到旧
    val grouped = remember(completedTasks) {
        val linked = LinkedHashMap<String, MutableList<TaskEntity>>()
        completedTasks.forEach { task ->
            val label = formatHistoryGroupLabel(historyAnchorMillis(task))
            linked.getOrPut(label) { mutableListOf() }.add(task)
        }
        linked
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "历史记录",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
            Text(
                text = if (trashedTasks.isNotEmpty())
                    "共 ${completedTasks.size} 条完成事项 · 回收站 ${trashedTasks.size} 条"
                else
                    "共 ${completedTasks.size} 条完成事项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, bottom = 10.dp)
            )

            if (grouped.isEmpty() && trashedTasks.isEmpty()) {
                Text(
                    text = "还没有完成记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (label, tasksInGroup) ->
                        item(key = "hist_header_$label") {
                            Text(
                                text = "$label · ${tasksInGroup.size}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }
                        items(tasksInGroup, key = { "hist_${it.id}" }) { task ->
                            AppleTaskCard(
                                task = task,
                                onToggle = { onToggleTask(task) },
                                onClick = { onEditTask(task) },
                                onTagClick = onTagClick
                            )
                        }
                    }

                    // 回收站：软删除事项，保留 30 天，可恢复或彻底删除
                    if (trashedTasks.isNotEmpty()) {
                        item(key = "trash_header") {
                            Column(modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "最近删除 (${trashedTasks.size})",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = "保留 ${CuonViewModel.TRASH_RETENTION_DAYS} 天后自动清理",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        items(trashedTasks, key = { "trash_${it.id}" }) { task ->
                            TrashRow(
                                task = task,
                                onRestore = { onRestoreTask(task) },
                                onDeleteForever = { onDeleteForever(task) }
                            )
                        }
                    }

                    item(key = "hist_bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/** 回收站单行：标题 + 剩余天数 + 恢复 / 彻底删除 */
@Composable
private fun TrashRow(
    task: TaskEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val remainingDays = remember(task.deletedAt) {
        val deletedAt = task.deletedAt
        if (deletedAt == null) CuonViewModel.TRASH_RETENTION_DAYS
        else {
            val days = (Calendar.getInstance().timeInMillis - deletedAt) / (24L * 60 * 60 * 1000)
            (CuonViewModel.TRASH_RETENTION_DAYS - days).coerceAtLeast(0)
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (remainingDays > 0) "剩 $remainingDays 天彻底删除" else "即将彻底删除",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            TextButton(onClick = onRestore) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("恢复", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = onDeleteForever) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
