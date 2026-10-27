package com.example.notes.ui.theme

import androidx.compose.ui.graphics.Color

// File này chỉ khai báo các màu cố định dùng trong app.
// Màu dạng 0xAARRGGBB: AA = độ đục (FF = đục hoàn toàn), RR/GG/BB = đỏ/xanh lá/xanh dương.

// --- Màu thương hiệu (dùng trong Theme.kt) ---
val Indigo600 = Color(0xFF4F46E5) // màu chính ở chế độ sáng
val Indigo300 = Color(0xFFA5B4FC) // màu chính ở chế độ tối
val Violet500 = Color(0xFF8B5CF6) // màu phụ

// --- Mỗi danh mục công việc có một màu riêng (xem CategoryStyle.kt) ---
val CategoryStudy = Color(0xFF6366F1) // Học tập
val CategoryWork = Color(0xFF0EA5E9) // Công việc
val CategoryPersonal = Color(0xFFEC4899) // Cá nhân
val CategoryHealth = Color(0xFF10B981) // Sức khỏe

// --- Màu cho mức ưu tiên ---
val PriorityHigh = Color(0xFFEF4444)
val PriorityMedium = Color(0xFFF59E0B)
val PriorityLow = Color(0xFF22C55E)

// --- Màu trạng thái ---
val DangerRed = Color(0xFFEF4444) // lỗi, quá hạn, trùng giờ
val WarningAmber = Color(0xFFF59E0B) // cảnh báo, chuỗi ngày (lửa)
val SuccessGreen = Color(0xFF22C55E) // hoàn thành

/**
 * Bảng màu pastel cho ghi chú.
 * Phần tử đầu (trong suốt) nghĩa là "màu mặc định" – ghi chú dùng màu nền của giao diện.
 */
val NotePalette = listOf(
    Color.Transparent,
    Color(0xFFFFE0E0), // hồng nhạt
    Color(0xFFFFEBCC), // cam nhạt
    Color(0xFFFFF6BF), // vàng nhạt
    Color(0xFFDDF5D6), // xanh lá nhạt
    Color(0xFFD3F3EE), // xanh ngọc nhạt
    Color(0xFFDCE9FF), // xanh dương nhạt
    Color(0xFFE9E0FF), // tím nhạt
    Color(0xFFFFDDF0) // hồng đậm nhạt
)
