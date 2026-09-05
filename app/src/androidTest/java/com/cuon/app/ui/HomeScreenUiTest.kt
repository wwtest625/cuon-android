package com.cuon.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cuon.app.data.local.TaskEntity
import org.junit.Rule
import org.junit.Test

/**
 * 官方标准 Compose 原生 UI 自动化测试套件
 * 涵盖：列表渲染、用户输入提交、任务打勾、手势左滑删除
 */
class HomeScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyState_displaysGuidance() {
        composeTestRule.setContent {
            HomeScreen(
                tasks = emptyList(),
                isProcessingAi = false,
                onToggleTask = {},
                onDeleteTask = {},
                onVoiceInputClick = {},
                onTextInputSubmit = {}
            )
        }

        // 验证空状态提示文案是否正确渲染
        composeTestRule.onNodeWithText("今天心智自由，无待办事项").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cuon AI 工作台").assertIsDisplayed()
    }

    @Test
    fun testTaskList_rendersCalendarAndTodoCards() {
        val mockTasks = listOf(
            TaskEntity(
                id = 1,
                title = "与李总商讨外贸合同",
                isCalendarEvent = true,
                startTime = System.currentTimeMillis() + 3600_000,
                location = "国贸 38F"
            ),
            TaskEntity(
                id = 2,
                title = "提交周报",
                isCalendarEvent = false,
                priority = "high",
                tag = "工作"
            )
        )

        composeTestRule.setContent {
            HomeScreen(
                tasks = mockTasks,
                isProcessingAi = false,
                onToggleTask = {},
                onDeleteTask = {},
                onVoiceInputClick = {},
                onTextInputSubmit = {}
            )
        }

        // 验证日程卡片渲染
        composeTestRule.onNodeWithText("与李总商讨外贸合同").assertIsDisplayed()
        composeTestRule.onNodeWithText("国贸 38F").assertIsDisplayed()

        // 验证待办任务卡片渲染
        composeTestRule.onNodeWithText("提交周报").assertIsDisplayed()
        composeTestRule.onNodeWithText("#工作").assertIsDisplayed()
    }

    @Test
    fun testInputAndSubmit_triggersCallback() {
        var submittedText = ""

        composeTestRule.setContent {
            HomeScreen(
                tasks = emptyList(),
                isProcessingAi = false,
                onToggleTask = {},
                onDeleteTask = {},
                onVoiceInputClick = {},
                onTextInputSubmit = { submittedText = it }
            )
        }

        // 查找输入框并模拟用户键盘输入
        val inputNode = composeTestRule.onNodeWithText("说出或粘贴杂乱事项，AI 自动拆解...")
        inputNode.performTextInput("明天上午开会")

        // 点击提交按钮
        composeTestRule.onNodeWithContentDescription("提交").performClick()

        // 断言回调触发
        assert(submittedText == "明天上午开会")
    }

    @Test
    fun testSwipeLeft_triggersDismiss() {
        var deletedTaskId = 0L
        val mockTasks = listOf(
            TaskEntity(id = 88, title = "测试滑动删除的事项", isCalendarEvent = false)
        )

        composeTestRule.setContent {
            HomeScreen(
                tasks = mockTasks,
                isProcessingAi = false,
                onToggleTask = {},
                onDeleteTask = { deletedTaskId = it.id },
                onVoiceInputClick = {},
                onTextInputSubmit = {}
            )
        }

        // 模拟向左滑动卡片手势
        composeTestRule.onNodeWithText("测试滑动删除的事项")
            .performTouchInput {
                swipeLeft()
            }

        // 验证删除回调触发
        assert(deletedTaskId == 88L)
    }
}
