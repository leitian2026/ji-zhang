package com.shopledger.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// QQ 风格蓝色配色。想换色：改这里，再把 themes.xml 里的状态栏色和 ic_launcher_background.xml 里的渐变色一起改。
val Brand = Color(0xFF12B7F5)        // 主色：按钮、光标
val BrandStart = Color(0xFF00C8FF)   // 顶栏渐变起点
val BrandEnd = Color(0xFF00A6FF)     // 顶栏渐变终点
val DeepOrange = Color(0xFF0B7FD8)   // 金额、累计
val ChatBg = Color(0xFFE9EDF7)       // 聊天背景
val BubbleMine = Color(0xFF1CB5F9)   // 我的气泡
val BubbleLine = Color(0xFFE1EAF5)   // 气泡里的表格线
val TotalStrip = Color(0xFFE9EDF7)   // 顶部"当前累计"条
val TableHeadBg = Color(0xFFEAF6FF)  // 表头底色
val TableZebra = Color(0xFFF6FAFF)   // 表格隔行底色
val Ink = Color(0xFF1B2330)          // 正文
val Muted = Color(0xFF8A93A3)        // 次要文字
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
