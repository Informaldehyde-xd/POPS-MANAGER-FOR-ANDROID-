package com.popsmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PopsBackground = Color(0xFF070B14)
val PopsSurface = Color(0xFF101A2E)
val PopsSurfaceElevated = Color(0xFF16223D)
val PopsPrimary = Color(0xFF3B82F6)
val PopsAccent = Color(0xFF60A5FA)
val PopsOnBackground = Color(0xFFE5EAF3)
val PopsOnSurfaceMuted = Color(0xFF8B98B4)
val PopsError = Color(0xFFEF4444)

private val PopsColorScheme = darkColorScheme(
    primary = PopsPrimary,
    onPrimary = Color.White,
    secondary = PopsAccent,
    onSecondary = Color.White,
    background = PopsBackground,
    onBackground = PopsOnBackground,
    surface = PopsSurface,
    onSurface = PopsOnBackground,
    surfaceVariant = PopsSurfaceElevated,
    onSurfaceVariant = PopsOnSurfaceMuted,
    error = PopsError,
    outline = PopsOnSurfaceMuted
)

@Composable
fun PopsManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PopsColorScheme, content = content)
}
