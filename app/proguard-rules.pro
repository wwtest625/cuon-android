# 保留数据实体与模型返回结构，防止 Release 打包时 Gson 反序列化字段丢失
-keepclassmembers class com.cuon.app.data.remote.** { *; }
-keepclassmembers class com.cuon.app.data.local.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room 规则
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
