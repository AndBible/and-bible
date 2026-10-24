package net.bible.service.common

import net.bible.sharedcore.platform.DateTimeFormats

private const val DOS_BEFORE_1980 = (1L shl 21) or (1L shl 16)

/**
 * The MS-DOS date/time of [epochMs] in the platform's local zone, as ZIP headers store it
 * (`date shl 16 or time`, seconds halved). Matches `java.util.zip.ZipEntry`, including its
 * clamp of years before 1980 to 1980-01-01 00:00.
 *
 * The digits come from [DateTimeFormats.pattern], so `digitToInt` accepts localized decimal digits.
 */
fun dosDateTime(epochMs: Long, formats: DateTimeFormats): Long {
    val s = formats.pattern("yyyyMMddHHmmss", epochMs)
    fun field(from: Int, to: Int): Int =
        s.substring(from, to).fold(0) { acc, c -> acc * 10 + c.digitToInt() }
    val year = field(0, 4)
    if (year < 1980) return DOS_BEFORE_1980
    val month = field(4, 6)
    val day = field(6, 8)
    val hour = field(8, 10)
    val minute = field(10, 12)
    val second = field(12, 14)
    return ((year - 1980).toLong() shl 25) or (month.toLong() shl 21) or (day.toLong() shl 16) or
        (hour.toLong() shl 11) or (minute.toLong() shl 5) or (second shr 1).toLong()
}
