/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.reading.session

/**
 * A link URL split into the parts [ReadingLinkHandler] reads; the string-based replacement for the
 * `android.net.Uri` calls `BibleView.openLink` used.
 *
 * @property scheme the scheme with its original case
 * @property authority the decoded text after `//` (empty for `scheme://?x`), null for a link without `//`
 * @property path the decoded path of a hierarchical link (empty when absent), null for an opaque link
 * @property query decoded parameter values by decoded name, names in first-occurrence order and repeated
 *   parameters collected in order; empty for an opaque link or a link without a query
 * @property schemeSpecificPart the decoded text between the scheme colon and the fragment
 */
internal data class ParsedLink(
    val scheme: String,
    val authority: String?,
    val path: String?,
    val query: Map<String, List<String>>,
    val schemeSpecificPart: String = "",
) {
    /** The first value of [name], like `Uri.getQueryParameter`. */
    fun param(name: String): String? = query[name]?.firstOrNull()

    /** Like `Uri.getBooleanQueryParameter`: absent gives [default], `false` and `0` (any case) are false. */
    fun booleanParam(name: String, default: Boolean): Boolean {
        val flag = param(name)?.lowercase() ?: return default
        return !(flag == "false" || flag == "0")
    }
}

/**
 * Parses [url] without any platform URI class. Returns null when [url] has no valid scheme.
 * Percent sequences are decoded as UTF-8 (a malformed one stays literal) and in query values `+` is a space.
 * A link whose scheme-specific part does not start with `/` is opaque: it has no authority, path or query.
 */
internal fun parseLink(url: String): ParsedLink? {
    val colon = url.indexOf(':')
    if (colon <= 0) return null
    val scheme = url.substring(0, colon)
    if (!scheme[0].isAsciiLetter() || !scheme.all { it.isAsciiLetter() || it in '0'..'9' || it == '+' || it == '-' || it == '.' }) {
        return null
    }
    val fragment = url.indexOf('#')
    val ssp = url.substring(colon + 1, if (fragment >= 0) fragment else url.length)
    val decodedSsp = percentDecode(ssp, plusAsSpace = false)
    if (!ssp.startsWith("/")) return ParsedLink(scheme, null, null, emptyMap(), decodedSsp)

    var rest = ssp
    var authority: String? = null
    if (ssp.startsWith("//")) {
        var end = 2
        while (end < ssp.length && ssp[end] != '/' && ssp[end] != '?') end++
        authority = percentDecode(ssp.substring(2, end), plusAsSpace = false)
        rest = ssp.substring(end)
    }
    val queryStart = rest.indexOf('?')
    val path = percentDecode(if (queryStart >= 0) rest.substring(0, queryStart) else rest, plusAsSpace = false)
    val query = if (queryStart >= 0) parseQuery(rest.substring(queryStart + 1)) else emptyMap()
    return ParsedLink(scheme, authority, path, query, decodedSsp)
}

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

private fun parseQuery(raw: String): Map<String, List<String>> {
    val result = LinkedHashMap<String, MutableList<String>>()
    for (pair in raw.split('&')) {
        if (pair.isEmpty()) continue
        val eq = pair.indexOf('=')
        val name = percentDecode(if (eq >= 0) pair.substring(0, eq) else pair, plusAsSpace = false)
        val value = if (eq >= 0) percentDecode(pair.substring(eq + 1), plusAsSpace = true) else ""
        result.getOrPut(name) { mutableListOf() }.add(value)
    }
    return result
}

private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

private fun percentDecode(s: String, plusAsSpace: Boolean): String {
    if (s.indexOf('%') < 0 && !(plusAsSpace && s.indexOf('+') >= 0)) return s
    val out = StringBuilder(s.length)
    val bytes = ArrayList<Byte>()
    fun flush() {
        if (bytes.isNotEmpty()) {
            out.append(bytes.toByteArray().decodeToString())
            bytes.clear()
        }
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length && hexValue(s[i + 1]) >= 0 && hexValue(s[i + 2]) >= 0) {
            bytes.add((hexValue(s[i + 1]) * 16 + hexValue(s[i + 2])).toByte())
            i += 3
            continue
        }
        flush()
        out.append(if (c == '+' && plusAsSpace) ' ' else c)
        i++
    }
    flush()
    return out.toString()
}
