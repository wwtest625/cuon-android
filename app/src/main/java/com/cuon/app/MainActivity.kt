package com.cuon.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.cuon.app.ui.CuonViewModel
import com.cuon.app.ui.HomeScreen
import com.cuon.app.ui.theme.CuonTheme
import java.util.*

class MainActivity : ComponentActivity() {

    // 使用 ViewModel 管理状态，屏幕旋转生命周期持久化
    private val viewModel by viewModels<CuonViewModel>()

    // 深链选中的日期时间戳
    private var initialSelectedDate by mutableStateOf<Long?>(null)

    // 通知权限请求启动器 (Android 13+)
    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "未开启通知权限，事项提醒将仅在应用内展示", Toast.LENGTH_LONG).show()
        }
    }

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

        // 处理从通知点击进来的深链参数
        handleDeepLinkIntent(intent)

        // Android 13+ 运行时通知权限检查与申请
        checkAndRequestNotificationPermission()

        setContent {
            CuonTheme {
                val tasks by viewModel.tasks.collectAsState()
                val isProcessing by viewModel.isProcessingAi.collectAsState()
                val taskGenerations by viewModel.taskGenerations.collectAsState()
                val draftTasks by viewModel.draftTasks.collectAsState()
                val editingTask by viewModel.editingTask.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // 监听删除消息，提供撤销 (Undo) 交互
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
                        draftTasks = draftTasks,
                        editingTask = editingTask,
                        initialSelectedDate = initialSelectedDate,
                        onToggleTask = { task ->
                            viewModel.toggleTask(task, delayMillis = 380)
                        },
                        onDeleteTask = { task ->
                            viewModel.deleteTask(task)
                        },
                        onUpdateTask = { task ->
                            viewModel.updateTask(task)
                        },
                        onEditTask = { task ->
                            viewModel.openTaskEditor(task)
                        },
                        onCloseTaskEditor = {
                            viewModel.closeTaskEditor()
                        },
                        onSaveEditedTask = { task ->
                            viewModel.saveEditedTask(task)
                        },
                        onDismissDraft = {
                            viewModel.clearDraftTasks()
                        },
                        onConfirmDraft = { confirmedList ->
                            viewModel.confirmAndSaveDraftTasks(confirmedList)
                        },
                        onUpdateDraftItem = { index, updated ->
                            viewModel.updateDraftTask(index, updated)
                        },
                        onRemoveDraftItem = { index ->
                            viewModel.removeDraftTask(index)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        intent?.let {
            if (it.hasExtra("EXTRA_SELECTED_DATE")) {
                val dateMillis = it.getLongExtra("EXTRA_SELECTED_DATE", 0L)
                if (dateMillis > 0) {
                    initialSelectedDate = dateMillis
                }
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出您要安排的生活事项...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "未检测到系统语音服务，可点击下方示例或直接输入", Toast.LENGTH_SHORT).show()
        }
    }
}
