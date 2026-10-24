package com.dang.glancecaps.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun appDark(): Boolean = when (UiState.themeMode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun GlanceCapsTheme(content: @Composable () -> Unit) {
    val dark = appDark()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFF6FE3A5),
            onPrimary = Color(0xFF00391F),
            primaryContainer = Color(0xFF00522F),
            onPrimaryContainer = Color(0xFF8CFFC0),
            tertiary = Color(0xFF7FD4F5),
            background = Color(0xFF111413),
            surface = Color(0xFF111413),
        )
        else -> lightColorScheme(
            primary = Color(0xFF00693A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF8CFFC0),
            onPrimaryContainer = Color(0xFF00210F),
            tertiary = Color(0xFF006688),
            background = Color(0xFFF6FBF5),
            surface = Color(0xFFF6FBF5),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
