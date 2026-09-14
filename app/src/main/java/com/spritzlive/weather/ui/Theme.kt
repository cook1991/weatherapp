package com.spritzlive.weather.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Accent = Color(0xFF00E5FF)
val AccentWarm = Color(0xFFFF6D00)
val DarkBg = Color(0xFF0A0E14)
val DarkSurface = Color(0xFF141B26)
val Muted = Color(0xFF8899AA)
val Track = Color(0xFF223040)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = AccentWarm,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006874),
    secondary = AccentWarm,
)

@Composable
fun WeatherTheme(
    // Matches the SkyCast/Speedometer dashboard look — dark by default to keep the
    // cyan/orange accents readable day and night. Ignores the system light setting.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
