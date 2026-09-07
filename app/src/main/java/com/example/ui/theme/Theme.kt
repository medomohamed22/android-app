package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = AiWayDarkPrimary,
    onPrimary = AiWayPrimaryDark,
    primaryContainer = AiWayDarkPrimaryContainer,
    onPrimaryContainer = AiWayPrimarySoft,
    secondary = AiWayPrimarySoft2,
    onSecondary = AiWayPrimaryDark,
    background = AiWayDarkBg,
    onBackground = AiWayDarkText,
    surface = AiWayDarkSurface,
    onSurface = AiWayDarkText,
    surfaceVariant = AiWayDarkSurfaceVariant,
    onSurfaceVariant = AiWayDarkMuted,
    outline = AiWayDarkBorder,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AiWayPrimary,
    onPrimary = Color.White,
    primaryContainer = AiWayPrimarySoft,
    onPrimaryContainer = AiWayPrimaryDark,
    secondary = AiWayPrimarySoft2,
    onSecondary = AiWayTextPrimary,
    background = AiWayBackground,
    onBackground = AiWayTextPrimary,
    surface = AiWaySurface,
    onSurface = AiWayTextPrimary,
    surfaceVariant = AiWaySurfaceVariant,
    onSurfaceVariant = AiWayTextMuted,
    outline = AiWayBorder,
    error = AiWayDanger,
    onError = Color.White
  )

@Composable
fun AiWayTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
