package com.shopledger.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 蓝色配色（跟互传一致）。想换色：改这里，再把 themes.xml 里的状态栏色和 ic_launcher_background.xml 里的渐变色一起改。
val Brand = Color(0xFF3D9BF0)        // 主色：按钮、光标
val BrandStart = Color(0xFF5CCBFB)   // 顶栏渐变起点
val BrandEnd = Color(0xFF4DA3F0)     // 顶栏渐变终点
val DeepOrange = Color(0xFF1F6FCF)   // 金额、累计
val ChatBg = Color(0xFFEBF1FB)       // 聊天背景
val BubbleMine = Color(0xFFD8EBFE)   // 我的气泡
val BubbleLine = Color(0xFFCADFF4)   // 气泡里的表格线
val TotalStrip = Color(0xFFEBF1FB)   // 顶部"当前累计"条
val TableHeadBg = Color(0xFFE7F2FE)  // 表头底色
val TableZebra = Color(0xFFF6FAFF)   // 表格隔行底色
val Ink = Color(0xFF1F2A37)          // 正文
val Muted = Color(0xFF7A8799)        // 次要文字
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
