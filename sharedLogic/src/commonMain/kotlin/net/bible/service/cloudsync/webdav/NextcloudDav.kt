package net.bible.service.cloudsync.webdav

private const val DAV_FILES = "remote.php/dav/files"

/**
 * Nextcloud's per-user WebDAV root, used by the iOS `NEXT_CLOUD` preset (spec C9).
 *
 * Handles a trailing slash, sub-path installs (`https://host/nextcloud`) and a [serverUrl] that
 * already is the user's DAV URL. The user name is percent-encoded exactly once: an already
 * encoded name (`anna%20maria`) is kept as is. The result always ends in a single `/`.
 */
fun nextcloudDavUrl(serverUrl: String, username: String): String {
    val base = serverUrl.trim().trimEnd('/')
    val user = percentEncodeOnce(username.trim())
    val marker = "/$DAV_FILES/"
    val at = base.indexOf(marker)
    if (at >= 0 || base.endsWith("/$DAV_FILES")) {
        // Already a DAV URL: keep it, just normalise the trailing slash.
        return "$base/"
    }
    return "$base/$DAV_FILES/$user/"
}

private fun percentEncodeOnce(s: String): String {
    val sb = StringBuilder()
    val bytes = s.encodeToByteArray()
    var i = 0
    while (i < bytes.size) {
        val b = bytes[i].toInt() and 0xFF
        val c = b.toChar()
        val isEscape = c == '%' && i + 2 < bytes.size && isHex(bytes[i + 1]) && isHex(bytes[i + 2])
        val unreserved = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c in "-._~"
        if (unreserved || isEscape) {
            sb.append(c)
        } else {
            sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0xF])
        }
        i++
    }
    return sb.toString()
}

private const val HEX = "0123456789ABCDEF"

private fun isHex(b: Byte): Boolean {
    val c = b.toInt().toChar()
    return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
