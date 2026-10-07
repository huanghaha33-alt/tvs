package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TvDarkColorScheme = darkColorScheme(
    primary = TvPrimary,
    secondary = TvSecondary,
    background = TvBackground,
    surface = TvSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TvTextPrimary,
    onSurface = TvTextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = Typography,
        content = content
    )
}
