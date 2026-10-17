package net.bible.sharedlogic.text

/**
 * `yyyy-MM-dd'T'HH:mm:ss'Z'` timestamps in UTC, common replacement for a `SimpleDateFormat` with that pattern.
 *
 * [parseLenient] mimics a lenient `SimpleDateFormat`: it matches a prefix of the text, ignores trailing text and
 * lets out-of-range fields roll over (month 13 is January of the next year, 30 February is 1 or 2 March, hour 24
 * is the next day). Dates are proleptic Gregorian (the JVM class switches to Julian before 1582).
 */
object IsoUtcTimestamps {
    private val PATTERN = Regex("""^(\d+)-(\d+)-(\d+)T(\d+):(\d+):(\d+)Z""")

    fun format(epochMs: Long): String {
        val totalSeconds = floorDiv(epochMs, 1000L)
        val days = floorDiv(totalSeconds, 86_400L)
        val secOfDay = (totalSeconds - days * 86_400L).toInt()
        // civil-from-days (Howard Hinnant), proleptic Gregorian, UTC.
        val z = days + 719_468
        val era = floorDiv(z, 146_097L)
        val doe = (z - era * 146_097).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "${pad(y.toInt(), 4)}-${pad(m, 2)}-${pad(d, 2)}T${pad(secOfDay / 3600, 2)}:" +
            "${pad(secOfDay / 60 % 60, 2)}:${pad(secOfDay % 60, 2)}Z"
    }

    fun parseLenient(text: String): Long {
        val g = PATTERN.find(text)?.groupValues
            ?: throw IllegalArgumentException("Unparseable date: \"$text\"")
        val year = g[1].toLong()
        val month0 = g[2].toLong() - 1
        val y = year + floorDiv(month0, 12L)
        val m = (month0 - floorDiv(month0, 12L) * 12).toInt() + 1
        return epochMillis(y, m, g[3].toLong(), g[4].toLong(), g[5].toLong(), g[6].toLong())
    }

    // Days-from-civil as in net.bible.sharedcore.webdav.HttpDates.epochMillis (internal there, copied on purpose).
    // m must be 1..12; d, h, mi and s may be any value and are added linearly (this is the roll-over).
    private fun epochMillis(y: Long, m: Int, d: Long, h: Long, mi: Long, s: Long): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = floorDiv(yy, 400L)
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097 + doe - 719468
        return ((days * 24 + h) * 60 + mi) * 60_000L + s * 1000L
    }

    private fun floorDiv(a: Long, b: Long): Long = a.floorDiv(b)

    private fun pad(n: Int, width: Int) = n.toString().padStart(width, '0')
}
