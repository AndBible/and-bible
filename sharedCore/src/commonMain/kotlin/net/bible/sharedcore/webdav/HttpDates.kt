package net.bible.sharedcore.webdav

/**
 * Minimal, allocation-light date parsing for WebDAV: RFC 1123 (`getlastmodified`, the `Date`
 * header) and ISO 8601 / RFC 3339 (`creationdate`). Pure Kotlin so it runs on iOS too; returns
 * epoch milliseconds, or null for anything it does not understand (callers treat null as
 * "unknown time", never as 0).
 */
object HttpDates {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val RFC1123 = Regex("""^\s*\w{3},\s+(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+GMT\s*$""")
    private val ISO8601 = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(\.\d+)?(Z|[+-]\d{2}:\d{2})$""")

    fun parseRfc1123(s: String): Long? {
        val m = RFC1123.matchEntire(s) ?: return null
        val (d, mon, y, h, mi, sec) = m.destructured
        val month = MONTHS.indexOf(mon.lowercase()) + 1
        if (month == 0) return null
        return epochMillis(y.toInt(), month, d.toInt(), h.toInt(), mi.toInt(), sec.toInt())
    }

    fun parseIso8601(s: String): Long? {
        val m = ISO8601.matchEntire(s.trim()) ?: return null
        val g = m.groupValues
        val base = epochMillis(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        val millis = g[7].takeIf { it.isNotEmpty() }?.drop(1)?.padEnd(3, '0')?.take(3)?.toInt() ?: 0
        val offsetMs = when (val z = g[8]) {
            "Z" -> 0L
            else -> {
                val sign = if (z[0] == '-') -1 else 1
                sign * (z.substring(1, 3).toLong() * 3_600_000 + z.substring(4, 6).toLong() * 60_000)
            }
        }
        return base + millis - offsetMs
    }

    /** Days-from-civil (Howard Hinnant); proleptic Gregorian, UTC. */
    internal fun epochMillis(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era.toLong() * 146097 + doe - 719468
        return ((days * 24 + h) * 60 + mi) * 60_000L + s * 1000L
    }
}
