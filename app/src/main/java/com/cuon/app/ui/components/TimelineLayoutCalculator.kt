package com.cuon.app.ui.components

import com.cuon.app.data.local.TaskEntity
import java.util.Calendar

data class TimelineEventLayout(
    val task: TaskEntity,
    val startMinute: Int,   // 从 00:00 起算的分钟数 (0..1440)
    val endMinute: Int,     // 从 00:00 起算的分钟数 (0..1440)
    val colIndex: Int,      // 在重叠簇中的列索引 (0..totalCols-1)
    val totalCols: Int      // 该重叠簇的总列数
)

data class FreeTimeSlot(
    val startMinute: Int,
    val endMinute: Int,
    val durationMinutes: Int = endMinute - startMinute
)

object TimelineLayoutCalculator {

    const val DEFAULT_START_HOUR = 7
    const val DEFAULT_END_HOUR = 23

    /**
     * 判断两个时间是否属于同一自然日 (基于 YEAR 和 DAY_OF_YEAR)
     */
    fun isSameDay(timeMillis: Long, targetCal: Calendar): Boolean {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        return cal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
    }

    fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 将时间戳转换为当天从 00:00 起算的分钟数 (0..1440)
     */
    fun getMinuteOfDay(timeMillis: Long, dayStartMillis: Long): Int {
        val diffMillis = timeMillis - dayStartMillis
        val minutes = (diffMillis / 60000L).toInt()
        return minutes.coerceIn(0, 1440)
    }

    /**
     * 获取指定日期的 00:00:00.000 毫秒时间戳
     */
    fun getStartOfDayMillis(calendar: Calendar): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, calendar.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, calendar.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * 核心重叠分列计算纯函数 (Interval Graph Coloring / Greedy Column Assignment)
     */
    fun calculateEventLayouts(
        events: List<TaskEntity>,
        dayStartMillis: Long
    ): List<TimelineEventLayout> {
        if (events.isEmpty()) return emptyList()

        data class RawLayout(
            val task: TaskEntity,
            val startMin: Int,
            val endMin: Int
        )

        val rawList = events.map { event ->
            val start = event.startTime ?: dayStartMillis
            val end = event.endTime ?: (start + 60 * 60 * 1000L) // 默认 1 小时

            var startMin = getMinuteOfDay(start, dayStartMillis)
            var endMin = getMinuteOfDay(end, dayStartMillis)

            if (endMin <= startMin) {
                endMin = (startMin + 30).coerceAtMost(1440)
            }
            // 保证最小可见高度 15 分钟
            if (endMin - startMin < 15) {
                endMin = (startMin + 15).coerceAtMost(1440)
            }

            RawLayout(event, startMin, endMin)
        }.sortedWith(compareBy({ it.startMin }, { -it.endMin }))

        // 2. 将事件切分为互不相交的连通重叠簇 (Clusters)
        val clusters = mutableListOf<MutableList<RawLayout>>()
        var currentCluster = mutableListOf<RawLayout>()
        var clusterEndMin = -1

        for (item in rawList) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(item)
                clusterEndMin = item.endMin
            } else {
                if (item.startMin < clusterEndMin) {
                    currentCluster.add(item)
                    clusterEndMin = maxOf(clusterEndMin, item.endMin)
                } else {
                    clusters.add(currentCluster)
                    currentCluster = mutableListOf(item)
                    clusterEndMin = item.endMin
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        // 3. 对每个重叠簇进行贪心列分配 (Greedy Column Coloring)
        val result = mutableListOf<TimelineEventLayout>()

        for (cluster in clusters) {
            val columnEndTimes = mutableListOf<Int>()
            val assignments = mutableMapOf<RawLayout, Int>()

            for (item in cluster) {
                var placedCol = -1
                for (col in columnEndTimes.indices) {
                    if (columnEndTimes[col] <= item.startMin) {
                        placedCol = col
                        columnEndTimes[col] = item.endMin
                        break
                    }
                }
                if (placedCol == -1) {
                    placedCol = columnEndTimes.size
                    columnEndTimes.add(item.endMin)
                }
                assignments[item] = placedCol
            }

            val totalCols = columnEndTimes.size
            for (item in cluster) {
                result.add(
                    TimelineEventLayout(
                        task = item.task,
                        startMinute = item.startMin,
                        endMinute = item.endMin,
                        colIndex = assignments[item] ?: 0,
                        totalCols = totalCols
                    )
                )
            }
        }

        return result
    }

    /**
     * V2 架构预留：计算指定时间段内的空闲时间槽 (Free Time Slots)
     */
    fun calculateFreeTimeSlots(
        events: List<TaskEntity>,
        dayStartMillis: Long,
        windowStartHour: Int = DEFAULT_START_HOUR,
        windowEndHour: Int = DEFAULT_END_HOUR,
        minSlotDurationMinutes: Int = 30
    ): List<FreeTimeSlot> {
        val windowStartMin = windowStartHour * 60
        val windowEndMin = windowEndHour * 60

        val busyIntervals = events.mapNotNull { event ->
            val start = event.startTime ?: return@mapNotNull null
            val end = event.endTime ?: (start + 60 * 60 * 1000L)
            val sMin = getMinuteOfDay(start, dayStartMillis).coerceIn(windowStartMin, windowEndMin)
            val eMin = getMinuteOfDay(end, dayStartMillis).coerceIn(windowStartMin, windowEndMin)
            if (eMin > sMin) sMin to eMin else null
        }.sortedBy { it.first }

        val mergedBusy = mutableListOf<Pair<Int, Int>>()
        for (interval in busyIntervals) {
            if (mergedBusy.isEmpty()) {
                mergedBusy.add(interval)
            } else {
                val last = mergedBusy.last()
                if (interval.first <= last.second) {
                    mergedBusy[mergedBusy.size - 1] = last.first to maxOf(last.second, interval.second)
                } else {
                    mergedBusy.add(interval)
                }
            }
        }

        val freeSlots = mutableListOf<FreeTimeSlot>()
        var currentPointer = windowStartMin

        for (busy in mergedBusy) {
            if (busy.first > currentPointer) {
                val duration = busy.first - currentPointer
                if (duration >= minSlotDurationMinutes) {
                    freeSlots.add(FreeTimeSlot(currentPointer, busy.first))
                }
            }
            currentPointer = maxOf(currentPointer, busy.second)
        }

        if (windowEndMin > currentPointer) {
            val duration = windowEndMin - currentPointer
            if (duration >= minSlotDurationMinutes) {
                freeSlots.add(FreeTimeSlot(currentPointer, windowEndMin))
            }
        }

        return freeSlots
    }

    /**
     * V2 架构预留：日程冲突检测 (Schedule Conflict Detection)
     */
    fun detectScheduleConflicts(events: List<TaskEntity>): List<Pair<TaskEntity, TaskEntity>> {
        val conflicts = mutableListOf<Pair<TaskEntity, TaskEntity>>()
        for (i in events.indices) {
            for (j in i + 1 until events.size) {
                val a = events[i]
                val b = events[j]
                val aStart = a.startTime ?: continue
                val aEnd = a.endTime ?: (aStart + 3600000L)
                val bStart = b.startTime ?: continue
                val bEnd = b.endTime ?: (bStart + 3600000L)

                if (maxOf(aStart, bStart) < minOf(aEnd, bEnd)) {
                    conflicts.add(a to b)
                }
            }
        }
        return conflicts
    }
}
