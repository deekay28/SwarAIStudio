package com.swarai.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Saffron = Color(0xFFFFB547)
val Coral = Color(0xFFFF7A59)
val Rani = Color(0xFFE8559B)
val Violet = Color(0xFFA06BFF)
val Night = Color(0xFF0E0B16)
val NightSurface = Color(0xFF17131F)
val NightSurfaceHigh = Color(0xFF221C2E)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF4A2F00),
    onPrimaryContainer = Color(0xFFFFDDB1),
    secondary = Rani,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4D1835),
    onSecondaryContainer = Color(0xFFFFD8E8),
    tertiary = Violet,
    onTertiary = Color.White,
    background = Night,
    onBackground = Color(0xFFECE6F3),
    surface = Night,
    onSurface = Color(0xFFECE6F3),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFC9C0D6),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerLow = Color(0xFF130F1A),
    outline = Color(0xFF5B5268),
    outlineVariant = Color(0xFF3A3346),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB1),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFFA0306A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E8),
    onSecondaryContainer = Color(0xFF3D0024),
    tertiary = Color(0xFF6B45C8),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    surfaceVariant = Color(0xFFF1E3EC),
    surfaceContainer = Color(0xFFF7EDF2),
    surfaceContainerHigh = Color(0xFFF1E5EC),
)

private val SwarTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    )
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun SwarTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = SwarTypography, content = content)
}
