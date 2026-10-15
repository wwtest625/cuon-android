package com.cuon.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.ui.formatHistoryGroupLabel
import com.cuon.app.ui.historyAnchorMillis

/**
 * 历史记录底部弹层：全部已完成事项按完成（归属）日期倒序分组，提供日历之外的浏览视角。
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
    onTagClick: (String) -> Unit = {}
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
                text = "共 ${completedTasks.size} 条完成事项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, bottom = 10.dp)
            )

            if (grouped.isEmpty()) {
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
                    item(key = "hist_bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
