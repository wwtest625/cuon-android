package com.cuon.app.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuon.app.BuildConfig
import com.cuon.app.data.local.AppDatabase
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.data.remote.AiParserService
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

    // 最近删除的任务（用于撤销 Undo）
    private var lastDeletedTask: TaskEntity? = null
    private val _undoMessage = MutableSharedFlow<Pair<String, TaskEntity>>()
    val undoMessage: SharedFlow<Pair<String, TaskEntity>> = _undoMessage.asSharedFlow()

    // 任务代次映射：防止撤销恢复后因 LazyColumn 复用相同 Key 导致滑动删除组件残留红色残影
    private val _taskGenerations = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val taskGenerations: StateFlow<Map<Long, Int>> = _taskGenerations.asStateFlow()

    /**
     * 处理自然语言输入：在 viewModelScope 中运行，屏幕旋转不中断！
     */
    fun processAndSaveInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(getApplication(), "未检测到输入内容，请说出或输入事项", Toast.LENGTH_SHORT).show()
            return
        }

        // 防重检查
        if (_isProcessingAi.value) return

        _isProcessingAi.value = true
        viewModelScope.launch {
            try {
                val parsedItems = aiService.parseTextToTasks(trimmed)
                if (parsedItems.isNotEmpty()) {
                    taskDao.insertTasks(parsedItems)

                    // 接入系统日历同步 (P3)
                    parsedItems.filter { it.isCalendarEvent }.forEach { event ->
                        CalendarSyncHelper.insertEventToSystemCalendar(getApplication(), event)
                    }

                    val calCount = parsedItems.count { it.isCalendarEvent }
                    val todoCount = parsedItems.size - calCount
                    Toast.makeText(
                        getApplication(),
                        "AI 拆解就绪：已安排 $calCount 场日程，$todoCount 项待办",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "网络超时，已存入普通待办", Toast.LENGTH_SHORT).show()
                taskDao.insertTask(TaskEntity(title = trimmed))
            } finally {
                _isProcessingAi.value = false
            }
        }
    }

    /**
     * 切换完成状态（支持延迟入库，让 UI 划线动画有充分时间展示）
     */
    fun toggleTask(task: TaskEntity, delayMillis: Long = 0) {
        viewModelScope.launch {
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    /**
     * 删除任务，并暂存以支持撤销 (P2 Undo)
     */
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            lastDeletedTask = task
            taskDao.deleteTask(task)
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
            taskDao.insertTask(taskToRestore)
            lastDeletedTask = null
            Toast.makeText(getApplication(), "已撤销删除", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新任务或日程内容 (用于编辑标题、时间或地点)
     */
    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task)
            Toast.makeText(getApplication(), "已保存修改", Toast.LENGTH_SHORT).show()
        }
    }
}
