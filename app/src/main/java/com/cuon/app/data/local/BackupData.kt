package com.cuon.app.data.local

/**
 * 数据备份文件结构（JSON，Gson 序列化）
 * tasks 含回收站事项（deletedAt 非空），places 为常用地点簿
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val defaultCity: String = "",
    val tasks: List<TaskEntity> = emptyList(),
    val places: List<FrequentPlaceEntity> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
