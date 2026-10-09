package net.bible.sharedcore.platform

/** Locale-aware date/time formatting that domain code needs. */
interface DateTimeFormats {
    fun shortDate(epochMs: Long): String
    fun shortTime(epochMs: Long): String
    fun relativeTimeSpan(epochMs: Long, nowMs: Long): String
    /** [epochMs] in the device locale and time zone by a fixed date [pattern] such as `"EEE, yyyy-MM-dd HH:mm"`. */
    fun pattern(pattern: String, epochMs: Long): String
}
