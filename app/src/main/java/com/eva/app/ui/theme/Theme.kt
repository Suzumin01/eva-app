package com.eva.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryBlue   = Color(0xFF1565C0)
private val SecondaryTeal = Color(0xFF00838F)
private val TertiaryGreen = Color(0xFF2E7D32)

private val LightColorScheme = lightColorScheme(
    primary            = PrimaryBlue,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFD6E4FF),
    onPrimaryContainer = PrimaryBlue,
    secondary          = SecondaryTeal,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFCCF0F3),
    tertiary           = TertiaryGreen,
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFC8E6C9),
    background         = Color(0xFFF8F9FA),
    surface            = Color.White,
    onBackground       = Color(0xFF1A1A2E),
    onSurface          = Color(0xFF1A1A2E),
    error              = Color(0xFFB00020)
)

private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFF90CAF9),  // Blue 200 — светлый, читается на тёмном
    onPrimary            = Color(0xFF00305A),
    primaryContainer     = Color(0xFF1A3F6F),
    onPrimaryContainer   = Color(0xFFD6E4FF),
    secondary            = Color(0xFF80DEEA),  // Cyan 200
    onSecondary          = Color(0xFF003F45),
    secondaryContainer   = Color(0xFF005057),
    onSecondaryContainer = Color(0xFFCCF0F3),
    tertiary             = Color(0xFFA5D6A7),  // Green 200
    onTertiary           = Color(0xFF1B4020),
    tertiaryContainer    = Color(0xFF1F4D24),
    onTertiaryContainer  = Color(0xFFC8E6C9),
    background           = Color(0xFF0F1923),
    surface              = Color(0xFF1A2530),
    surfaceVariant       = Color(0xFF263040),
    onBackground         = Color(0xFFE1E8EF),
    onSurface            = Color(0xFFE1E8EF),
    onSurfaceVariant     = Color(0xFFB0BEC5),
    error                = Color(0xFFCF6679),
    onError              = Color(0xFF601410),
    outline              = Color(0xFF546E7A),
    outlineVariant       = Color(0xFF37474F)
)

@Composable
fun EvaTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = Typography(),
        content     = content
    )
}