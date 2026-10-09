package net.bible.sharedcore.webdav

/**
 * Maps between AndBible's WebDAV **paths** (relative to the configured root, no leading/trailing
 * slash, unencoded, `""` = root) and request URLs / response hrefs. Servers return hrefs either as
 * absolute paths (`/dav/x`) or full URLs, percent-encoded with either hex case; both are accepted.
 */
class DavPath(baseUrl: String) {
    val baseUrl: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    /** The base URL's path component, decoded, always ending with "/". */
    private val basePathDecoded: String = run {
        val afterScheme = this.baseUrl.substringAfter("://")
        val rawPath = "/" + afterScheme.substringAfter('/', "")
        decodePercent(rawPath)
    }

    fun url(path: String): String =
        if (path.isEmpty()) baseUrl
        else baseUrl + path.split('/').joinToString("/") { encodeSegment(it) }

    fun relativize(href: String): String? {
        val rawPath = if (href.contains("://")) "/" + href.substringAfter("://").substringAfter('/', "") else href
        val decoded = decodePercent(rawPath)
        val withSlash = if (decoded.endsWith("/")) decoded else "$decoded/"
        if (!withSlash.startsWith(basePathDecoded)) return null
        return withSlash.removePrefix(basePathDecoded).trimEnd('/')
    }
}

fun joinDavPath(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private const val HEX = "0123456789ABCDEF"

internal fun encodeSegment(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = (b.toInt() and 0xFF)
        if (c < 0x80 && UNRESERVED.indexOf(c.toChar()) >= 0) append(c.toChar())
        else { append('%'); append(HEX[c shr 4]); append(HEX[c and 0xF]) }
    }
}

/** Percent-decoding of a URL path. `+` stays `+` (it is only a space in query strings). */
internal fun decodePercent(s: String): String {
    if ('%' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        val hi = if (ch == '%' && i + 2 < s.length) hexValue(s[i + 1]) else -1
        val lo = if (hi >= 0) hexValue(s[i + 2]) else -1
        if (lo >= 0) {
            out.add(((hi shl 4) or lo).toByte()); i += 3
        } else {
            ch.toString().encodeToByteArray().forEach { out.add(it) }; i++
        }
    }
    return out.toByteArray().decodeToString()
}

private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
