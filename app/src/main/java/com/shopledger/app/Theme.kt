package com.shopledger.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 主色：墨绿。想换色改这里和 res/values/themes.xml、colors.xml 里的 #1E6B52。
val Green = Color(0xFF1E6B52)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD5EADF),
            onPrimaryContainer = Color(0xFF0B3B2C),
            background = Color(0xFFF3F7F5),
            surface = Color.White
        ),
        content = content
    )
}
