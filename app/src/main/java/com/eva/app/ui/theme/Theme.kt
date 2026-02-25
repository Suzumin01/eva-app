package com.eva.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryBlue    = Color(0xFF1565C0)
private val PrimaryLight   = Color(0xFF42A5F5)
private val SecondaryTeal  = Color(0xFF00838F)
private val TertiaryGreen  = Color(0xFF2E7D32)

private val LightColorScheme = lightColorScheme(
    primary          = PrimaryBlue,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    secondary        = SecondaryTeal,
    onSecondary      = Color.White,
    tertiary         = TertiaryGreen,
    background       = Color(0xFFF8F9FA),
    surface          = Color.White,
    onBackground     = Color(0xFF1A1A2E),
    onSurface        = Color(0xFF1A1A2E)
)

@Composable
fun EvaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography(),
        content     = content
    )
}
