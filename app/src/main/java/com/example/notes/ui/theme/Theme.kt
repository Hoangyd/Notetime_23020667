package com.example.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Blue60,
    secondary = Aqua40,
    tertiary = Blue40,
    background = WarmGray,
    surface = Color.White,
    surfaceVariant = Color(0xFFE2E8EF),
    onPrimary = Color.White,
    onBackground = Slate,
    onSurface = Slate,
    onSurfaceVariant = Color(0xFF4E5968)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CC8FF),
    secondary = Color(0xFF7BC9C9),
    tertiary = Color(0xFFA8CBFF),
    background = Color(0xFF10151D),
    surface = Color(0xFF18202A),
    surfaceVariant = Color(0xFF293545),
    onPrimary = Color(0xFF0F304C),
    onBackground = Color(0xFFE3E8F0),
    onSurface = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFFB7C2D3)
)

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content
    )
}
