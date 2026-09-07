package com.cuon.app.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuon.app.BuildConfig
import com.cuon.app.data.local.AppDatabase
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.data.remote.AiParserService
import com.cuon.app.reminder.ReminderScheduler
import com.cuon.app.util.CalendarSyncHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CuonViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()

    private val aiService = AiParserService(
        apiKey = BuildConfig.AI_API_KEY,
        apiBaseUrl = BuildConfig.AI_BASE_URL,
        modelName = BuildConfig.AI_MODEL
    )

    // 全量任务流（屏幕旋转不丢失）
    val tasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasksFlow()
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
        // App 启动时，异步核对并重新调度所有未来提醒
        viewModelScope.launch {
            try {
                ReminderScheduler.rescheduleAllFutureReminders(application)
            } catch (e: Exception) {
                // 忽略非致命异常
            }
        }
    }

    /**
     * 处理自然语言输入：AI 拆解后生成草稿，弹窗供用户核对纠错，不盲目直接入库
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
                val parsedItems = aiService.parseTextToTasks(trimmed)
                aiService.lastFallbackReason?.let { reason ->
                    Toast.makeText(getApplication(), "$reason，已将原文存为待办，可稍后重新拆解", Toast.LENGTH_LONG).show()
                }
                val defaultRemindedItems = parsedItems.map { item ->
                    if (item.reminderMinutesBefore == null) {
                        if (item.isCalendarEvent) item.copy(reminderMinutesBefore = 15)
                        else if (item.endTime != null) item.copy(reminderMinutesBefore = 0)
                        else item
                    } else item
                }
                if (defaultRemindedItems.isNotEmpty()) {
                    _draftTasks.value = defaultRemindedItems
                } else {
                    _draftTasks.value = listOf(TaskEntity(title = trimmed))
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
            val updated = task.copy(isCompleted = !task.isCompleted)
            taskDao.updateTask(updated)
            ReminderScheduler.scheduleReminder(getApplication(), updated)
        }
    }

    /**
     * 删除任务，并暂存以支持撤销 (P2 Undo)
     */
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            lastDeletedTask = task
            taskDao.deleteTask(task)
            ReminderScheduler.cancelReminder(getApplication(), task.id)
            if (_editingTask.value?.id == task.id) {
                _editingTask.value = null
            }
            _undoMessage.emit(Pair("已删除：${task.title.take(10)}...", task))
        }
    }

    /**
     * 撤销删除恢复任务
     */
    fun undoDelete() {
        val taskToRestore = lastDeletedTask ?: return
        viewModelScope.launch {
            val nextGen = (_taskGenerations.value[taskToRestore.id] ?: 0) + 1
            _taskGenerations.value = _taskGenerations.value + (taskToRestore.id to nextGen)
            val newId = taskDao.insertTask(taskToRestore)
            val restored = taskToRestore.copy(id = if (newId > 0) newId else taskToRestore.id)
            ReminderScheduler.scheduleReminder(getApplication(), restored)
            lastDeletedTask = null
            Toast.makeText(getApplication(), "已撤销删除", Toast.LENGTH_SHORT).show()
        }
    }
}
