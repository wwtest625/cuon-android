package com.cuon.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.cuon.app.ui.CuonViewModel
import com.cuon.app.ui.HomeScreen
import com.cuon.app.ui.theme.CuonTheme
import java.util.*

class MainActivity : ComponentActivity() {

    // 使用 ViewModel 管理状态，屏幕旋转生命周期持久化 (解决 P0)
    private val viewModel by viewModels<CuonViewModel>()

    // 原生语音识别启动器
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                viewModel.processAndSaveInput(spokenText)
            } else {
                Toast.makeText(this, "未检测到语音内容", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CuonTheme {
                val tasks by viewModel.tasks.collectAsState()
                val isProcessing by viewModel.isProcessingAi.collectAsState()
                val taskGenerations by viewModel.taskGenerations.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // 监听删除消息，提供撤销 (Undo) 交互 (解决 P2)
                LaunchedEffect(Unit) {
                    viewModel.undoMessage.collect { (msg, _) ->
                        val result = snackbarHostState.showSnackbar(
                            message = msg,
                            actionLabel = "撤销",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDelete()
                        }
                    }
                }

                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                actionColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) { padding ->
                    HomeScreen(
                        tasks = tasks,
                        isProcessingAi = isProcessing,
                        taskGenerations = taskGenerations,
                        onToggleTask = { task ->
                            // 延时 380ms 入库，留足前端打勾与划线动画展示时间 (解决 P2)
                            viewModel.toggleTask(task, delayMillis = 380)
                        },
                        onDeleteTask = { task ->
                            viewModel.deleteTask(task)
                        },
                        onUpdateTask = { task ->
                            viewModel.updateTask(task)
                        },
                        onVoiceInputClick = {
                            startSpeechToText()
                        },
                        onTextInputSubmit = { text ->
                            viewModel.processAndSaveInput(text)
                        }
                    )
                }
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
}
