package com.cuon.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 常用地点：语义标签 + 完整地址 + 城市。
 * 用于 AI 解析时把"回家/去公司/接孩子"这类指代直接映射到具体地址。
 */
@Entity(tableName = "frequent_places")
data class FrequentPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,     // 家 / 公司 / 孩子学校 / 自定义
    val address: String,   // 完整拼接地址（POI 回填文本）
    val city: String       // 所在城市，如"杭州"
)
