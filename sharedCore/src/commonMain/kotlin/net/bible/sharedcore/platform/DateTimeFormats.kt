package net.bible.sharedcore.platform

/** Locale-aware date/time formatting that domain code needs. */
interface DateTimeFormats {
    fun shortDate(epochMs: Long): String
    fun shortTime(epochMs: Long): String
    fun relativeTimeSpan(epochMs: Long, nowMs: Long): String
}
