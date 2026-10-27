package com.example.notes.ui.util

import com.example.notes.domain.TimeUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Các hàm đổi ngày / giờ / thời lượng thành chữ tiếng Việt để hiển thị.
 *
 * Nhắc lại cách app lưu thời gian (xem domain/TimeUtils.kt):
 *  - "epochDay"    = số ngày kể từ 1/1/1970 (dùng cho một NGÀY).
 *  - "epochMillis" = số mili-giây kể từ 1/1/1970 (dùng cho một THỜI ĐIỂM).
 */
object Fmt {
    private val vi = Locale("vi", "VN")

    // Mẫu định dạng. Ví dụ "HH:mm" -> "14:30"; "d/M" -> "24/10".
    // Chữ trong dấu nháy đơn '...' được giữ nguyên.
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", vi)
    private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, d 'tháng' M", vi)
    private val shortDayFormat = DateTimeFormatter.ofPattern("d/M", vi)
    private val monthTitleFormat = DateTimeFormatter.ofPattern("'Tháng' M, yyyy", vi)
    private val fullDateFormat = DateTimeFormatter.ofPattern("EEE, d/M/yyyy", vi)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** Lời chào theo giờ trong ngày. */
    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> "Chào buổi sáng"
        in 11..12 -> "Chào buổi trưa"
        in 13..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }

    /** 1729764000000 -> "14:30" */
    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone()).format(timeFormat)

    /** Số phút kể từ nửa đêm -> "HH:mm". Ví dụ 570 -> "09:30". */
    fun minutesOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** epochDay -> "Thứ Bảy, 24 tháng 10" */
    fun dayTitle(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(dayTitleFormat).replaceFirstChar { it.uppercase(vi) }

    /** epochDay -> "24/10" */
    fun shortDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDayFormat)

    /** epochDay -> "Tháng 10, 2026" */
    fun monthTitle(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(monthTitleFormat)

    /** epochDay -> "Th 7, 24/10/2026" */
    fun fullDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(fullDateFormat)

    /** Tên thứ viết tắt: T2, T3, ..., CN. */
    fun weekdayShort(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "T2"
        DayOfWeek.TUESDAY -> "T3"
        DayOfWeek.WEDNESDAY -> "T4"
        DayOfWeek.THURSDAY -> "T5"
        DayOfWeek.FRIDAY -> "T6"
        DayOfWeek.SATURDAY -> "T7"
        DayOfWeek.SUNDAY -> "CN"
    }

    fun weekdayShort(epochDay: Long): String = weekdayShort(LocalDate.ofEpochDay(epochDay).dayOfWeek)

    /** Nhãn 7 thứ theo thứ tự thứ Hai -> Chủ nhật (khớp với bit 0..6 của "daysMask"). */
    val weekdayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

    /** Mili-giây -> "1g 30p", "45p", "2g". */
    fun duration(ms: Long): String {
        val minutes = (ms / 60_000L).toInt()
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "${rest}p"
            rest == 0 -> "${hours}g"
            else -> "${hours}g ${rest}p"
        }
    }

    /** Số phút -> "1g 30p". */
    fun minutes(minutes: Int): String = duration(minutes * 60_000L)

    /**
     * Mô tả hạn chót cho dễ đọc:
     * "Hôm nay 18:00", "Ngày mai 09:00", "Quá hạn 2 ngày", "25/10 09:00".
     */
    fun deadline(deadline: Long, now: Long = System.currentTimeMillis()): String {
        val today = TimeUtils.epochDay(now)
        val day = TimeUtils.epochDay(deadline)
        val time = time(deadline)
        return when {
            deadline < now && day == today -> "Hôm nay $time (đã qua)"
            deadline < now -> "Quá hạn ${today - day} ngày"
            day == today -> "Hôm nay $time"
            day == today + 1 -> "Ngày mai $time"
            else -> "${shortDay(day)} $time"
        }
    }
}
