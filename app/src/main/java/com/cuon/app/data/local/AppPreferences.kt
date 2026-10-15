package com.cuon.app.data.local

import android.content.Context

/**
 * 应用级非结构化偏好（SharedPreferences 薄封装）。
 * 目前只有"默认城市"；未来回收站保留期、提醒默认值等标量放这里。
 */
class AppPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("cuon_prefs", Context.MODE_PRIVATE)

    /** 用户主要生活城市（如"杭州"），AI 解析省略城市时的默认值 */
    var defaultCity: String
        get() = prefs.getString(KEY_DEFAULT_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEFAULT_CITY, value.trim()).apply()

    companion object {
        private const val KEY_DEFAULT_CITY = "default_city"

        @Volatile private var INSTANCE: AppPreferences? = null

        fun get(context: Context): AppPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context).also { INSTANCE = it }
            }
    }
}
