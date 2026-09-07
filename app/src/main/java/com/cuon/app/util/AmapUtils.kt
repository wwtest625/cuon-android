package com.cuon.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

/**
 * 跳转高德地图 POI 搜索(仅高德,不 fallback 其他地图)
 * scheme: androidamap://poi?sourceApplication=cuon&keywords=xxx
 */
fun openInAmap(context: Context, keyword: String) {
    if (keyword.isBlank()) return
    val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://poi?sourceApplication=cuon&keywords=$encoded"))
        .setPackage("com.autonavi.minimap") // 只允许高德地图处理
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "未安装高德地图", Toast.LENGTH_SHORT).show()
    }
}
