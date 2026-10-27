package com.example.notes.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Days are stored as epoch-day numbers (local calendar date), instants as epoch millis. */
object TimeUtils {
    fun epochDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    fun dayStart(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayEnd(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dayStart(epochDay + 1, zone)

    /** Epoch day of the Monday of the week containing [epochDay]. */
    fun weekStart(epochDay: Long): Long {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek
        return epochDay - (dow.value - DayOfWeek.MONDAY.value)
    }

    /** Epoch millis of [minutes] after midnight on [epochDay] in the local zone. */
    fun at(epochDay: Long, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()

    /** Minutes after local midnight for [millis]. */
    fun minutesOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    fun hourOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(millis).atZone(zone).hour

    /** Bit mask with bit 0 = Monday ... bit 6 = Sunday. */
    const val ALL_DAYS_MASK = 0b1111111

    fun isDayInMask(mask: Int, epochDay: Long): Boolean {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value // 1 = Monday
        return mask and (1 shl (dow - 1)) != 0
    }
}
