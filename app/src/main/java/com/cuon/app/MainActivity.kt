package com.cuon.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.cuon.app.data.local.AppDatabase
import com.cuon.app.data.local.TaskEntity
import com.cuon.app.data.remote.AiParserService
import com.cuon.app.ui.HomeScreen
import com.cuon.app.ui.theme.CuonTheme
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val taskDao by lazy { db.taskDao() }
    
    // 从 BuildConfig 安全读取配置（敏感 Key 受 local.properties 保护，不进版本库）
    private val aiService by lazy {
        AiParserService(
            apiKey = BuildConfig.AI_API_KEY,
            apiBaseUrl = BuildConfig.AI_BASE_URL,
            modelName = BuildConfig.AI_MODEL
        )
    }

    private var isProcessingAiState = mutableStateOf(false)

    // 原生语音识别启动器
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                processAndSaveInput(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CuonTheme {
                val tasks by taskDao.getAllTasksFlow().collectAsState(initial = emptyList())
                val isProcessing by isProcessingAiState

                HomeScreen(
                    tasks = tasks,
                    isProcessingAi = isProcessing,
                    onToggleTask = { task ->
                        lifecycleScope.launch {
                            taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))
                        }
                    },
                    onDeleteTask = { task ->
                        lifecycleScope.launch {
                            taskDao.deleteTask(task)
                        }
                    },
                    onVoiceInputClick = {
                        startSpeechToText()
                    },
                    onTextInputSubmit = { text ->
                        processAndSaveInput(text)
                    }
                )
            }
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出您要安排的事情...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "未检测到系统语音服务，可点击下方示例或直接输入", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processAndSaveInput(rawText: String) {
        isProcessingAiState.value = true
        lifecycleScope.launch {
            try {
                val parsedItems = aiService.parseTextToTasks(rawText)
                if (parsedItems.isNotEmpty()) {
                    taskDao.insertTasks(parsedItems)
                    val calCount = parsedItems.count { it.isCalendarEvent }
                    val todoCount = parsedItems.size - calCount
                    Toast.makeText(
                        this@MainActivity,
                        "AI 拆解就绪：已安排 $calCount 场日程，$todoCount 项待办",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "网络超时，已存入普通待办", Toast.LENGTH_SHORT).show()
                taskDao.insertTask(TaskEntity(title = rawText))
            } finally {
                isProcessingAiState.value = false
            }
        }
    }
}
