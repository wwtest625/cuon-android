package com.cuon.app.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuon.app.BuildConfig
import com.cuon.app.data.local.AppDatabase
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.data.remote.AiOutcome
import com.cuon.app.data.remote.AiParserService
import com.cuon.app.data.remote.AiToolCall
import com.cuon.app.reminder.CalendarAutoArchiver
import com.cuon.app.reminder.MorningDigestScheduler
import com.cuon.app.reminder.RecurrenceAdvancer
import com.cuon.app.reminder.RecurrenceHelper
import com.cuon.app.reminder.ReminderScheduler
import com.cuon.app.util.CalendarSyncHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CuonViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CuonViewModel"

    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()

    private val aiService = AiParserService(
        apiKey = BuildConfig.AI_API_KEY,
        apiBaseUrl = BuildConfig.AI_BASE_URL,
        modelName = BuildConfig.AI_MODEL
    )

    // 全量任务流（屏幕旋转不丢失），不含回收站事项
    val tasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 回收站（软删除事项，30 天后自动彻底清理）
    val trash: StateFlow<List<TaskEntity>> = taskDao.getTrashFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI 处理状态
    private val _isProcessingAi = MutableStateFlow(false)
    val isProcessingAi: StateFlow<Boolean> = _isProcessingAi.asStateFlow()

    // AI 拆解草稿流（方案 A：不直接入库，先弹拆解预览供逐项纠错、编辑、确认）
    private val _draftTasks = MutableStateFlow<List<TaskEntity>?>(null)
    val draftTasks: StateFlow<List<TaskEntity>?> = _draftTasks.asStateFlow()

    // 正在编辑的任务（通用编辑 BottomSheet，任意卡片/时间块点击触发）
    private val _editingTask = MutableStateFlow<TaskEntity?>(null)
    val editingTask: StateFlow<TaskEntity?> = _editingTask.asStateFlow()

    // 最近删除的任务（用于撤销 Undo）
    private var lastDeletedTask: TaskEntity? = null
    private val _undoMessage = MutableSharedFlow<Pair<String, TaskEntity>>()
    val undoMessage: SharedFlow<Pair<String, TaskEntity>> = _undoMessage.asSharedFlow()

    // 任务代次映射：防止撤销恢复后因 LazyColumn 复用相同 Key 导致滑动删除组件残留红色残影
    private val _taskGenerations = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val taskGenerations: StateFlow<Map<Long, Int>> = _taskGenerations.asStateFlow()

    init {
        // App 启动时，异步核对并重新调度所有未来提醒与每日晨报
        viewModelScope.launch {
            try {
                ReminderScheduler.rescheduleAllFutureReminders(application)
                // 自愈式：晨报闹钟若被系统清理或基准时间漂移，这里会被纠正回来
                MorningDigestScheduler.ensureDailyDigest(application)
            } catch (e: Exception) {
                Log.w(TAG, "启动期提醒重排失败: ${e.message}")
            }
        }
        // 回收站过期清理：删除超过 30 天的事项彻底移除
        viewModelScope.launch {
            try {
                val purged = taskDao.purgeExpiredTrash(
                    System.currentTimeMillis() - TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
                )
                if (purged > 0) Log.i(TAG, "回收站过期清理: $purged 条")
            } catch (e: Exception) {
                Log.w(TAG, "回收站过期清理失败: ${e.message}")
            }
        }
        // 日程自动归档：距上次晨报之后打开 App 也能把到期日程落进历史
        viewModelScope.launch {
            try {
                CalendarAutoArchiver.scanAndArchive(application)
            } catch (e: Exception) {
                Log.w(TAG, "启动期日程归档失败: ${e.message}")
            }
        }
    }

    /**
     * 处理自然语言输入：AI 拆解新事项生成草稿（方案A，核对后入库），
     * 或路由 AI 返回的管理工具指令（改期/删除/完成/查询）直接执行
     */
    fun processAndSaveInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(getApplication(), "未检测到输入内容，请说出或输入事项", Toast.LENGTH_SHORT).show()
            return
        }

        // 防重检查（交互已解锁，重复提交在此拦截并给出反馈）
        if (_isProcessingAi.value) {
            Toast.makeText(getApplication(), "AI 正在拆解上一条，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        _isProcessingAi.value = true
        viewModelScope.launch {
            try {
                when (val outcome = aiService.processInput(trimmed)) {
                    is AiOutcome.ToolCommands -> executeToolCommands(outcome.calls)
                    is AiOutcome.TaskDrafts -> {
                        aiService.lastFallbackReason?.let { reason ->
                            Toast.makeText(getApplication(), "$reason，已将原文存为待办，可稍后重新拆解", Toast.LENGTH_LONG).show()
                        }
                        val defaultRemindedItems = outcome.items.map { applyDefaultReminder(it) }
                        _draftTasks.value = if (defaultRemindedItems.isNotEmpty()) {
                            defaultRemindedItems
                        } else {
                            listOf(TaskEntity(title = trimmed))
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "网络超时，已生成普通待办草稿", Toast.LENGTH_SHORT).show()
                _draftTasks.value = listOf(TaskEntity(title = trimmed))
            } finally {
                _isProcessingAi.value = false
            }
        }
    }

    /**
     * 执行 AI 返回的事项管理工具指令（增删改查路由）
     */
    private fun executeToolCommands(calls: List<AiToolCall>) {
        viewModelScope.launch {
            val feedback = mutableListOf<String>()
            val pendingAdds = mutableListOf<TaskEntity>()
            for (call in calls) {
                try {
                    when (call.name) {
                        "add_task" -> pendingAdds.add(buildTaskFromToolArgs(call.args))
                        "update_task" -> feedback.add(executeUpdateTask(call.args))
                        "delete_task" -> feedback.add(executeDeleteTask(call.args))
                        "complete_task" -> feedback.add(executeCompleteTask(call.args))
                        "query_tasks" -> feedback.add(executeQueryTasks(call.args))
                        else -> feedback.add("未知的操作: ${call.name}")
                    }
                } catch (e: Exception) {
                    feedback.add("操作 ${call.name} 执行失败")
                }
            }
            if (pendingAdds.isNotEmpty()) {
                // 方案A：新增也走草稿预览，由用户核对后入库
                _draftTasks.value = (_draftTasks.value ?: emptyList()) + pendingAdds
                feedback.add("已生成 ${pendingAdds.size} 条新事项草稿，请核对确认")
            }
            if (feedback.isNotEmpty()) {
                Toast.makeText(getApplication(), feedback.joinToString("；"), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 新事项默认提醒策略：日程提前 15 分钟，有截止时间的准时提醒 */
    private fun applyDefaultReminder(item: TaskEntity): TaskEntity =
        if (item.reminderMinutesBefore == null) {
            if (item.isCalendarEvent) item.copy(reminderMinutesBefore = 15)
            else if (item.endTime != null) item.copy(reminderMinutesBefore = 0)
            else item
        } else item

    /** 把 add_task 工具参数转为任务实体 */
    private fun buildTaskFromToolArgs(args: Map<String, Any?>): TaskEntity {
        val title = (args["title"] as? String)?.trim().orEmpty()
        val isCalendar = when (val raw = args["is_calendar_event"]) {
            is Boolean -> raw
            is String -> raw.lowercase() == "true"
            else -> false
        }
        val priority = (args["priority"] as? String)?.trim()?.lowercase()
            ?.takeIf { it in setOf("high", "medium", "low") } ?: "medium"
        val tag = (args["tag"] as? String)?.trim().orEmpty()
        val entity = TaskEntity(
            title = title.ifBlank { "未命名事项" },
            isCalendarEvent = isCalendar,
            startTime = aiService.parseDateStringToMillis(args["start_time"] as? String),
            endTime = aiService.parseDateStringToMillis(args["end_time"] as? String),
            priority = priority,
            location = (args["location"] as? String)?.trim().orEmpty(),
            tag = tag.ifBlank { if (isCalendar) "日程" else "待办" },
            reminderMinutesBefore = (args["reminder_minutes_before"] as? Number)?.toInt(),
            recurrenceRule = RecurrenceHelper.normalize(args["recurrence"] as? String)
        )
        return applyDefaultReminder(entity)
    }

    /** 按标题关键词匹配唯一事项；0 条或多条均返回提示语。改期等场景需能命中已完成事项 */
    private suspend fun matchTask(keyword: String?, includeCompleted: Boolean = false): Pair<TaskEntity?, String?> {
        val kw = keyword?.trim().orEmpty()
        if (kw.isBlank()) return null to "没听清要处理哪件事，请再说一次"
        val pool = if (includeCompleted) taskDao.getAllTasksOnce() else taskDao.getPendingTasks()
        val candidates = pool
            .filter { it.title.contains(kw, ignoreCase = true) }
        return when {
            candidates.isEmpty() -> null to "没找到「${kw.take(10)}」相关事项"
            candidates.size > 1 -> {
                val names = candidates.take(3).joinToString("、") { "「${it.title.take(8)}」" }
                null to "匹配到 ${candidates.size} 条 $names，请说得更具体些"
            }
            else -> candidates.first() to null
        }
    }

    private suspend fun executeUpdateTask(args: Map<String, Any?>): String {
        val match = matchTask(args["title_keyword"] as? String, includeCompleted = true)
        val task: TaskEntity = match.first ?: return match.second ?: "未匹配到事项"
        var updated = task
        (args["new_title"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { updated = updated.copy(title = it) }
        (args["new_start_time"] as? String)
            ?.let { updated = updated.copy(startTime = aiService.parseDateStringToMillis(it)) }
        (args["new_end_time"] as? String)
            ?.let { updated = updated.copy(endTime = aiService.parseDateStringToMillis(it)) }
        (args["new_priority"] as? String)?.trim()?.lowercase()
            ?.takeIf { it in setOf("high", "medium", "low") }
            ?.let { updated = updated.copy(priority = it) }
        (args["new_location"] as? String)?.let { updated = updated.copy(location = it.trim()) }
        (args["new_reminder_minutes_before"] as? Number)
            ?.let { updated = updated.copy(reminderMinutesBefore = it.toInt()) }
        if (args.containsKey("new_recurrence")) {
            // normalize("none") 返回 null，等价于清除重复规则
            updated = updated.copy(recurrenceRule = RecurrenceHelper.normalize(args["new_recurrence"] as? String))
        }
        (args["mark_completed"])?.let { v ->
            val mark = (v as? Boolean) ?: (v as? String) == "true"
            if (mark) updated = updated.copy(isCompleted = true, completedAt = System.currentTimeMillis())
        }
        taskDao.updateTask(updated)
        ReminderScheduler.scheduleReminder(getApplication(), updated)
        if (updated.isCompleted && !task.isCompleted && RecurrenceHelper.isRecurrence(task)) {
            advanceRecurrence(task)
        }
        return "已更新「${task.title.take(10)}」"
    }

    private suspend fun executeDeleteTask(args: Map<String, Any?>): String {
        val match = matchTask(args["title_keyword"] as? String)
        val task: TaskEntity = match.first ?: return match.second ?: "未匹配到事项"
        lastDeletedTask = task
        taskDao.softDeleteTask(task.id, System.currentTimeMillis())
        ReminderScheduler.cancelReminder(getApplication(), task.id)
        _undoMessage.emit(Pair("已删除：${task.title.take(10)}", task))
        return "已删除「${task.title.take(10)}」，30 天内可在回收站找回"
    }

    private suspend fun executeCompleteTask(args: Map<String, Any?>): String {
        val match = matchTask(args["title_keyword"] as? String)
        val task: TaskEntity = match.first ?: return match.second ?: "未匹配到事项"
        val completed = task.copy(isCompleted = true, completedAt = System.currentTimeMillis())
        taskDao.updateTask(completed)
        ReminderScheduler.scheduleReminder(getApplication(), completed)
        if (RecurrenceHelper.isRecurrence(task)) advanceRecurrence(task)
        return "已完成「${task.title.take(10)}」💪"
    }

    private suspend fun executeQueryTasks(args: Map<String, Any?>): String {
        val status = (args["status"] as? String)?.trim()?.lowercase().orEmpty()
        val keyword = (args["keyword"] as? String)?.trim()
        val scope = (args["scope"] as? String)?.trim()?.lowercase().orEmpty()

        val all = when (status) {
            "completed" -> taskDao.getAllTasksOnce().filter { it.isCompleted }
            "all" -> taskDao.getAllTasksOnce()
            else -> taskDao.getPendingTasks()
        }
        var filtered = all
        if (!keyword.isNullOrBlank()) {
            filtered = filtered.filter { it.title.contains(keyword, ignoreCase = true) }
        }
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L
        when (scope) {
            "today" -> filtered = filtered.filter {
                (it.startTime ?: it.endTime)?.let { t -> t in dayStart until dayEnd } == true
            }
            "upcoming" -> filtered = filtered.filter {
                val t = it.startTime ?: it.endTime
                t != null && t >= dayStart
            }
        }
        if (filtered.isEmpty()) return "没有符合条件的事项"

        val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
        val sorted = filtered.sortedBy { it.startTime ?: it.endTime ?: Long.MAX_VALUE }
        val shown = sorted.take(5).map { t ->
            val t0 = t.startTime ?: t.endTime
            if (t0 != null) "「${t.title.take(10)}」${fmt.format(Date(t0))}" else "「${t.title.take(10)}」"
        }
        val more = if (sorted.size > 5) " 等 ${sorted.size} 件" else ""
        return "共 ${sorted.size} 件：${shown.joinToString("、")}$more"
    }

    /**
     * 更新草稿列表中的某一项
     */
    fun updateDraftTask(index: Int, updated: TaskEntity) {
        val current = _draftTasks.value?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = updated
            _draftTasks.value = current
        }
    }

    /**
     * 丢弃草稿列表中的某一项
     */
    fun removeDraftTask(index: Int) {
        val current = _draftTasks.value?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _draftTasks.value = if (current.isEmpty()) null else current
        }
    }

    /**
     * 关闭或放弃草稿
     */
    fun clearDraftTasks() {
        _draftTasks.value = null
    }

    /**
     * 用户核对并确认入库
     */
    fun confirmAndSaveDraftTasks(confirmedItems: List<TaskEntity>) {
        if (confirmedItems.isEmpty()) {
            _draftTasks.value = null
            return
        }
        viewModelScope.launch {
            val insertedList = mutableListOf<TaskEntity>()
            for (item in confirmedItems) {
                val newId = taskDao.insertTask(item)
                val saved = item.copy(id = newId)
                insertedList.add(saved)
                // 安排分钟级精确提醒
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            }
            _draftTasks.value = null

            // 接入系统日历同步 (P3)
            insertedList.filter { it.isCalendarEvent }.forEach { event ->
                CalendarSyncHelper.insertEventToSystemCalendar(getApplication(), event)
            }

            val calCount = insertedList.count { it.isCalendarEvent }
            val todoCount = insertedList.size - calCount
            Toast.makeText(
                getApplication(),
                "已确认记入：$calCount 场日程，$todoCount 项生活事项",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 打开通用编辑 BottomSheet
     */
    fun openTaskEditor(task: TaskEntity) {
        _editingTask.value = task
    }

    /**
     * 关闭通用编辑 BottomSheet
     */
    fun closeTaskEditor() {
        _editingTask.value = null
    }

    /**
     * 保存编辑修改（联动更新 DB 与闹钟排期）
     */
    fun saveEditedTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task)
            ReminderScheduler.scheduleReminder(getApplication(), task)
            _editingTask.value = null
            Toast.makeText(getApplication(), "已保存修改", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 兼容接口：更新任务
     */
    fun updateTask(task: TaskEntity) {
        saveEditedTask(task)
    }

    /**
     * 切换完成状态（支持延迟入库，让 UI 划线动画有充分时间展示）
     */
    fun toggleTask(task: TaskEntity, delayMillis: Long = 0) {
        viewModelScope.launch {
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            // 以旧值判断：完成 → 记录完成时间；取消完成 → 清空（历史记录随之移出"该日完成"）
            val updated = task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
            taskDao.updateTask(updated)
            ReminderScheduler.scheduleReminder(getApplication(), updated)
            // 重复任务完成时推进下一周期实例（每周一倒垃圾这类）
            if (updated.isCompleted && RecurrenceHelper.isRecurrence(task)) {
                advanceRecurrence(task)
            }
        }
    }

    /**
     * 重复任务完成后生成下一个未来周期的实例（规则见 RecurrenceAdvancer）。
     */
    private suspend fun advanceRecurrence(task: TaskEntity) {
        val newId = RecurrenceAdvancer.advance(taskDao, task) ?: return
        val rule = task.recurrenceRule ?: return
        val anchor = task.startTime ?: task.endTime ?: return
        val nextAnchor = RecurrenceHelper.nextOccurrence(rule, anchor)
        val shift = nextAnchor - anchor
        fun s(t: Long?): Long? = t?.let { it + shift }
        ReminderScheduler.scheduleReminder(
            getApplication(),
            task.copy(id = newId, isCompleted = false, completedAt = null, startTime = s(task.startTime), endTime = s(task.endTime))
        )
    }

    /**
     * 删除任务（软删除进回收站），并暂存以支持撤销 (P2 Undo)
     */
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            lastDeletedTask = task
            taskDao.softDeleteTask(task.id, System.currentTimeMillis())
            ReminderScheduler.cancelReminder(getApplication(), task.id)
            if (_editingTask.value?.id == task.id) {
                _editingTask.value = null
            }
            _undoMessage.emit(Pair("已删除，可在回收站找回", task))
        }
    }

    /**
     * 撤销删除恢复任务（从回收站移回）
     */
    fun undoDelete() {
        val taskToRestore = lastDeletedTask ?: return
        restoreTask(taskToRestore)
        lastDeletedTask = null
    }

    /** 从回收站恢复任务 */
    fun restoreTask(task: TaskEntity) {
        viewModelScope.launch {
            val nextGen = (_taskGenerations.value[task.id] ?: 0) + 1
            _taskGenerations.value = _taskGenerations.value + (task.id to nextGen)
            taskDao.restoreTask(task.id)
            ReminderScheduler.scheduleReminder(getApplication(), task)
            Toast.makeText(getApplication(), "已恢复：${task.title.take(10)}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 回收站中彻底删除（不可恢复） */
    fun deleteForever(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
            Toast.makeText(getApplication(), "已彻底删除", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TRASH_RETENTION_DAYS = 30L
    }
}
