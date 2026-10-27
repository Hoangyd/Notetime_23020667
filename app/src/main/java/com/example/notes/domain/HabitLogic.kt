package com.example.notes.domain

object StreakCalculator {
    private const val MAX_LOOKBACK_DAYS = 3650

    /**
     * Consecutive scheduled days completed, ending today. A pending (not yet done) today does
     * not break the streak. Days outside [mask] are skipped, but still count if they were done.
     */
    fun current(done: Set<Long>, mask: Int, today: Long): Int {
        if (mask == 0 || done.isEmpty()) return 0
        val earliest = done.min()
        var day = if (today in done) today else today - 1
        var count = 0
        var steps = 0
        while (day >= earliest && steps++ < MAX_LOOKBACK_DAYS) {
            if (day in done) {
                count++
            } else if (TimeUtils.isDayInMask(mask, day)) {
                break
            }
            day--
        }
        return count
    }

    fun longest(done: Set<Long>, mask: Int): Int {
        if (mask == 0 || done.isEmpty()) return 0
        var best = 0
        var run = 0
        for (day in done.min()..done.max()) {
            if (day in done) {
                run++
                if (run > best) best = run
            } else if (TimeUtils.isDayInMask(mask, day)) {
                run = 0
            }
        }
        return best
    }

    /** Share of scheduled days in [fromDay]..[toDay] that were completed (0f when none scheduled). */
    fun completionRate(done: Set<Long>, mask: Int, fromDay: Long, toDay: Long): Float {
        if (toDay < fromDay) return 0f
        var scheduled = 0
        var completed = 0
        for (day in fromDay..toDay) {
            if (TimeUtils.isDayInMask(mask, day)) {
                scheduled++
                if (day in done) completed++
            }
        }
        return if (scheduled == 0) 0f else completed.toFloat() / scheduled
    }
}
