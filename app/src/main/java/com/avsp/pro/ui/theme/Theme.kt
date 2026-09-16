package com.avsp.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Studio-focused palette — teal/slate, not purple-on-white defaults.
private val TealPrimary = Color(0xFF0F766E)
private val TealDark = Color(0xFF134E4A)
private val AccentAmber = Color(0xFFD97706)
private val SlateBg = Color(0xFFF1F5F9)
private val SlateSurface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    secondary = AccentAmber,
    onSecondary = Color.White,
    tertiary = TealDark,
    background = SlateBg,
    onBackground = Ink,
    surface = SlateSurface,
    onSurface = Ink,
    error = Color(0xFFB91C1C),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF422006),
    tertiary = Color(0xFF99F6E4),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

@Composable
fun AvspTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AvspTypography,
        content = content
    )
}
