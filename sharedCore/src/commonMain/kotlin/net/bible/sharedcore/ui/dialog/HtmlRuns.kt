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

package net.bible.sharedcore.ui.dialog

/** One styled stretch of text parsed from a dialog body (spec §6.1). */
data class HtmlRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val size: Size = Size.Normal,
    val href: String? = null,
) {
    enum class Size { Small, Normal, Big }
}

private val TAG = Regex("""<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9]*)([^<>]*?)(/?)\s*>""")
private val HREF = Regex("""href\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")
private val NAMED = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
    "apos" to "'", "bull" to "•",
)

/**
 * Parses the small HTML subset the app's dialog bodies use into styled runs, matching
 * `Html.fromHtml(…, FROM_HTML_MODE_LEGACY)` for that subset. Never throws and never drops text:
 * an unknown tag is removed with its text kept, a `<` that starts no tag is literal.
 */
fun parseHtmlRuns(html: String): List<HtmlRun> {
    val out = ArrayList<HtmlRun>()
    var bold = 0; var italic = 0; var big = 0; var small = 0
    val hrefs = ArrayList<String?>()
    val sb = StringBuilder()

    fun style() = HtmlRun(
        text = "", bold = bold > 0, italic = italic > 0,
        size = if (big > small) HtmlRun.Size.Big else if (small > big) HtmlRun.Size.Small else HtmlRun.Size.Normal,
        href = hrefs.lastOrNull { it != null },
    )
    var current = style()

    fun flush() {
        if (sb.isNotEmpty()) {
            val last = out.lastOrNull()
            if (last != null && last.copy(text = "") == current) out[out.size - 1] = last.copy(text = last.text + sb)
            else out += current.copy(text = sb.toString())
            sb.clear()
        }
    }
    fun restyle() { val s = style(); if (s != current) { flush(); current = s } }
    fun lastChar(): Char? = if (sb.isNotEmpty()) sb.last() else out.lastOrNull()?.text?.lastOrNull()
    fun appendText(raw: String) {
        for (ch in decodeEntities(raw)) {
            if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r') {
                val prev = lastChar()
                if (prev != null && prev != ' ' && prev != '\n') sb.append(' ')
            } else sb.append(ch)
        }
    }
    fun lineBreak(n: Int) {
        // Drop a trailing collapsed space before the break, then add n newlines (never more than n in a row).
        if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        var existing = 0
        var i = sb.length - 1
        while (i >= 0 && sb[i] == '\n') { existing++; i-- }
        repeat((n - existing).coerceAtLeast(0)) { sb.append('\n') }
    }

    var pos = 0
    for (m in TAG.findAll(html)) {
        appendText(html.substring(pos, m.range.first))
        pos = m.range.last + 1
        val closing = m.groupValues[1] == "/"
        val selfClosing = m.groupValues[4] == "/"
        when (m.groupValues[2].lowercase()) {
            "br" -> lineBreak(1)
            "p", "div" -> if (lastChar() != null) lineBreak(2)
            "b", "strong" -> { bold += if (closing) -1 else if (selfClosing) 0 else 1; bold = bold.coerceAtLeast(0); restyle() }
            "i", "em" -> { italic += if (closing) -1 else if (selfClosing) 0 else 1; italic = italic.coerceAtLeast(0); restyle() }
            "big" -> { big += if (closing) -1 else 1; big = big.coerceAtLeast(0); restyle() }
            "small" -> { small += if (closing) -1 else 1; small = small.coerceAtLeast(0); restyle() }
            "a" -> {
                if (closing) { if (hrefs.isNotEmpty()) hrefs.removeAt(hrefs.size - 1) }
                else {
                    val h = HREF.find(m.groupValues[3])?.let { it.groupValues[2].ifEmpty { it.groupValues[3].ifEmpty { it.groupValues[4] } } }
                    hrefs += h
                }
                restyle()
            }
            else -> Unit   // unknown tag: dropped, its text is kept by the surrounding appendText calls
        }
    }
    appendText(html.substring(pos))
    flush()
    return out
}

private fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
        body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { codePointToString(it) }
        else -> NAMED[body]
    } ?: m.value
}

private fun codePointToString(cp: Int): String =
    if (cp < 0x10000) cp.toChar().toString()
    else { val v = cp - 0x10000; charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString() }
