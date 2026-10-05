package com.brkckr.parkv3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1453A3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    surface = Color(0xFFFBFCFF),
    background = Color(0xFFFBFCFF),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    surface = Color(0xFF111318),
    background = Color(0xFF111318),
)

/**
 * Status colors. They are always paired with an icon and a text label, so color is never
 * the only carrier of meaning. Container/content pairs keep at least 4.5:1 contrast.
 */
@Immutable
data class StatusColors(
    val availableContainer: Color,
    val onAvailable: Color,
    val fullContainer: Color,
    val onFull: Color,
    val unknownContainer: Color,
    val onUnknown: Color,
)

private val LightStatus = StatusColors(
    availableContainer = Color(0xFFC8F0D2), onAvailable = Color(0xFF0B3D1C),
    fullContainer = Color(0xFFFFDAD6), onFull = Color(0xFF5F0A0A),
    unknownContainer = Color(0xFFFFE8B3), onUnknown = Color(0xFF3F2E00),
)

private val DarkStatus = StatusColors(
    availableContainer = Color(0xFF1C5130), onAvailable = Color(0xFFC8F0D2),
    fullContainer = Color(0xFF7A1F1A), onFull = Color(0xFFFFDAD6),
    unknownContainer = Color(0xFF5B4300), onUnknown = Color(0xFFFFE8B3),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun ParkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
