package com.avsp.pro.capture.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun accentColor(accent: AppAccentOption): Color = when (accent) {
    AppAccentOption.BLUE -> Indigo500
    AppAccentOption.CYAN -> Color(0xFF06B6D4)
    AppAccentOption.EMERALD -> Emerald500
    AppAccentOption.AMBER -> Amber500
    AppAccentOption.ROSE -> Rose500
}

private fun accentDark(accent: AppAccentOption): Color = when (accent) {
    AppAccentOption.BLUE -> Indigo600
    AppAccentOption.CYAN -> Color(0xFF0891B2)
    AppAccentOption.EMERALD -> Color(0xFF059669)
    AppAccentOption.AMBER -> Color(0xFFD97706)
    AppAccentOption.ROSE -> Color(0xFFE11D48)
}

@Composable
fun AVSPTheme(
    darkTheme: Boolean = when (AppPreferences.theme) {
        AppThemeOption.SYSTEM -> isSystemInDarkTheme()
        AppThemeOption.LIGHT -> false
        AppThemeOption.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val accent = accentColor(AppPreferences.accent)
    val accentDarkColor = accentDark(AppPreferences.accent)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = Emerald500,
            background = Slate950,
            surface = Slate900,
            surfaceVariant = Slate800,
            onPrimary = Color.White,
            onBackground = Slate100,
            onSurface = Slate100,
            onSurfaceVariant = Slate300
        )
    } else {
        lightColorScheme(
            primary = accentDarkColor,
            secondary = accentDarkColor,
            tertiary = Emerald500,
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            surfaceVariant = Slate100,
            onPrimary = Color.White,
            onBackground = Slate950,
            onSurface = Slate950,
            onSurfaceVariant = Slate600
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
