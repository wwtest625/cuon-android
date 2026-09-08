package com.cuon.app.reminder

import com.cuon.app.data.local.TaskEntity
import java.util.*

/**
 * 重复任务规则（RRULE 极简子集，不引外部库）：
 * DAILY=每天、WEEKLY=每周、MONTHLY=每月（按自然月推进，月末自动收敛）。
 * 周期锚点取 startTime 优先、endTime 兜底；完成任务时推进生成下一个未来实例。
 */
object RecurrenceHelper {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    val SUPPORTED = listOf(DAILY, WEEKLY, MONTHLY)

    fun isRecurrence(task: TaskEntity): Boolean = task.recurrenceRule in SUPPORTED

    /** 规范化外部输入（AI 返回值），非法值返回 null */
    fun normalize(raw: String?): String? {
        val upper = raw?.trim()?.uppercase() ?: return null
        return if (upper in SUPPORTED) upper else null
    }

    /**
     * 计算下一个严格未来的周期锚点：至少推进一个周期，再追赶跳过已过去的时段
     */
    fun nextOccurrence(rule: String, anchor: Long, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        var next = anchor
        do {
            cal.timeInMillis = next
            when (rule) {
                DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                MONTHLY -> cal.add(Calendar.MONTH, 1)
            }
            next = cal.timeInMillis
        } while (next <= now)
        return next
    }
}
