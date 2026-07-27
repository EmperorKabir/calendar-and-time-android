package com.kabirbhasin.statuscalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Pure-black AMOLED palette; dark is the app's default and only theme. */
private val AmoledColors = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceVariant = Color(0xFF141414)
)

@Composable
fun StatusCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmoledColors, content = content)
}
