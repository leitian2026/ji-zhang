package com.shopledger.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 暖橙配色。想换色：改这里，再把 themes.xml 里的状态栏色和 ic_launcher_background.xml 里的渐变色一起改。
val Brand = Color(0xFFE8640F)        // 主色：按钮、光标
val BrandStart = Color(0xFFFFA04D)   // 顶栏渐变起点
val BrandEnd = Color(0xFFFF7A3D)     // 顶栏渐变终点
val DeepOrange = Color(0xFFC2410C)   // 金额、累计
val ChatBg = Color(0xFFFFF6EC)       // 聊天背景
val BubbleMine = Color(0xFFFFE2C8)   // 我的气泡
val BubbleLine = Color(0xFFF3C9A6)   // 气泡里的表格线
val TotalStrip = Color(0xFFFFEBD9)   // 顶部"当前累计"条
val Ink = Color(0xFF3B2A1F)          // 正文
val Muted = Color(0xFF9A8576)        // 次要文字
val SysAvatar = Color(0xFFF2A93B)    // 结算气泡的头像色

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            primaryContainer = BubbleMine,
            onPrimaryContainer = Ink,
            background = ChatBg,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink
        ),
        content = content
    )
}
