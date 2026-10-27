package com.example.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Material 3 gom các màu thành một "ColorScheme" với tên theo vai trò
// (primary = màu chính, surface = nền thẻ, background = nền màn hình, ...).
// Các thành phần như Button, Card tự lấy màu từ đây, nên đổi ở một chỗ là đổi cả app.

/** Bảng màu cho chế độ sáng. */
private val LightScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White, // "on..." = màu chữ/biểu tượng nằm trên màu đó
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1B1B6B),
    secondary = Violet500,
    onSecondary = Color.White,
    tertiary = CategoryHealth,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14161F),
    surface = Color.White,
    onSurface = Color(0xFF14161F),
    surfaceVariant = Color(0xFFECEEF6),
    onSurfaceVariant = Color(0xFF5B6074),
    outline = Color(0xFFC8CCDC),
    outlineVariant = Color(0xFFE3E5EF),
    error = DangerRed
)

/** Bảng màu cho chế độ tối. */
private val DarkScheme = darkColorScheme(
    primary = Indigo300,
    onPrimary = Color(0xFF1B1B6B),
    primaryContainer = Color(0xFF34358F),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF2E1B6B),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF0F111A),
    onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF181B27),
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF252938),
    onSurfaceVariant = Color(0xFFA9AEC4),
    outline = Color(0xFF4A4F66),
    outlineVariant = Color(0xFF2C3045),
    error = Color(0xFFFF8A8A)
)

/**
 * "CompositionLocal" = một giá trị mà mọi composable bên trong đều đọc được
 * mà không cần truyền qua tham số. Ở đây: app đang ở chế độ tối hay không.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Bọc toàn bộ app. Mọi composable bên trong `content` sẽ dùng màu và kiểu chữ của app.
 */
@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // mặc định theo cài đặt của điện thoại
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content
        )
    }
}

/**
 * Màu nền của ghi chú.
 * - [stored] = 0 nghĩa là chưa chọn màu → dùng [default].
 * - Ở chế độ tối, màu pastel quá sáng nên được pha thêm màu tối cho dễ nhìn.
 */
fun noteContainerColor(stored: Int, dark: Boolean, default: Color): Color {
    if (stored == 0) return default
    val color = Color(stored)
    return if (dark) lerp(color, Color(0xFF14161F), 0.72f) else color
}
