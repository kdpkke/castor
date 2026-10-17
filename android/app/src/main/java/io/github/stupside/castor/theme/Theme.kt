package io.github.stupside.castor.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light =
  lightColorScheme(
    primary = Color(0xFF904B25),
    secondary = Color(0xFF4F6338),
    background = Color(0xFFFFF8EE),
    surface = Color(0xFFFFF8EE),
    onSurface = Color(0xFF24231F),
  )
private val dark =
  darkColorScheme(
    primary = Color(0xFFE9A77E),
    secondary = Color(0xFFB4CA94),
    background = Color(0xFF24231F),
    surface = Color(0xFF24231F),
    onSurface = Color(0xFFECD6B6),
  )

@Composable
fun CastorTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) dark else light,
    typography = Typography,
    content = content,
  )
}
