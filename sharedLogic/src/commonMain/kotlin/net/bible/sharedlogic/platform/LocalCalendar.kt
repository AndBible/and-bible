package net.bible.sharedlogic.platform

/** Local-time calendar arithmetic. iOS provides this from NSCalendar.current (DST-aware, locale first weekday). */
interface LocalCalendar {
    fun startOfLocalDay(epochMs: Long): Long
    fun plusLocalDays(epochMs: Long, days: Int): Long
    fun plusWeeks(epochMs: Long, weeks: Int): Long
    fun startOfLocalWeek(epochMs: Long): Long
    /** Month 0-11. */
    fun localMonth(epochMs: Long): Int
    fun shortMonthName(epochMs: Long): String
}
