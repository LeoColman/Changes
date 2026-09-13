// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9ECEF),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF6B5B3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4DFB8),
    onSecondaryContainer = Color(0xFF241A04),
    background = Color(0xFFFBFCFB),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFBFCFB),
    onSurface = Color(0xFF191C1C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD0D4),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF144B50),
    onPrimaryContainer = Color(0xFFB9ECEF),
    secondary = Color(0xFFD8C39D),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF53452A),
    onSecondaryContainer = Color(0xFFF4DFB8),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE0E3E2),
)

@Composable
fun ChangesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
