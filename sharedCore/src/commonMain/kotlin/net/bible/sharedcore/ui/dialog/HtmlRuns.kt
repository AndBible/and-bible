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
// A terminated comment/declaration is dropped whole (never matched by TAG, since neither starts
// with an optional '/' followed by a letter); an UNTERMINATED `<!--` matches neither regex below,
// so it is left for TAG to also skip over, falling through to plain text -- the "never drops text"
// contract (I1).
private val COMMENT = Regex("""<!--[\s\S]*?-->""")
private val DECL = Regex("""<![A-Za-z][^>]*>""")
private val NAMED = mapOf(
    // nbsp decodes to a REAL non-breaking space (U+00A0), not ' ' -- appendText's whitespace
    // collapsing only matches the ASCII ' '/'\n'/'\t'/'\r' checks below, so this one is never
    // collapsed against a neighbouring space, matching Html.fromHtml (run-1 minor).
    "nbsp" to "\u00A0", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
    "apos" to "'", "bull" to "•",
)

/**
 * Parses the small HTML subset the app's dialog bodies use into styled runs, matching
 * `Html.fromHtml(…, FROM_HTML_MODE_LEGACY)` for that subset. Never throws and never drops text:
 * an unknown tag is removed with its text kept, a `<` that starts no tag is literal.
 */
fun parseHtmlRuns(rawHtml: String): List<HtmlRun> {
    // Comments and declarations (`<!DOCTYPE …>`) are stripped before the tag scan even sees them --
    // TAG requires `[a-zA-Z]` right after `<` (plus an optional `/`), so `<!--`/`<!DOCTYPE` would
    // otherwise fall into the "unknown tag" `else` branch and vanish silently AS a tag, dropping
    // their `>`-containing bodies incorrectly. Stripping first keeps both the "matches Html.fromHtml"
    // and "never drops text outside a real tag" contracts (I1).
    val html = DECL.replace(COMMENT.replace(rawHtml, ""), "")
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
        // A style change (restyle()) can have just flushed sb into `out`, e.g. a heading's closing
        // tag decrementing bold/big right after its own lineBreak(2) -- so an EMPTY sb here does not
        // mean zero trailing newlines: the run just pushed to `out` may already end in some. Without
        // counting those too, a block tag immediately following such a flush (I1's headings/lists
        // next to another block tag) would add n MORE on top, doubling the blank line.
        val trailingSource: CharSequence? = if (sb.isNotEmpty()) sb else out.lastOrNull()?.text
        if (trailingSource != null) {
            var i = trailingSource.length - 1
            while (i >= 0 && trailingSource[i] == '\n') { existing++; i-- }
        }
        repeat((n - existing).coerceAtLeast(0)) { sb.append('\n') }
    }
    fun brBreak() {
        // Html.fromHtml(FROM_HTML_MODE_LEGACY): every <br> is its own newline, unlike <p>/<div>'s
        // dedup -- two consecutive <br> make a blank line. Still drop a trailing collapsed space.
        if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        sb.append('\n')
    }

    var pos = 0
    for (m in TAG.findAll(html)) {
        appendText(html.substring(pos, m.range.first))
        pos = m.range.last + 1
        val closing = m.groupValues[1] == "/"
        val selfClosing = m.groupValues[4] == "/"
        when (m.groupValues[2].lowercase()) {
            "br" -> brBreak()
            "p", "div" -> if (lastChar() != null) lineBreak(2)
            "b", "strong" -> { bold += if (closing) -1 else if (selfClosing) 0 else 1; bold = bold.coerceAtLeast(0); restyle() }
            "i", "em" -> { italic += if (closing) -1 else if (selfClosing) 0 else 1; italic = italic.coerceAtLeast(0); restyle() }
            "big" -> { big += if (closing) -1 else 1; big = big.coerceAtLeast(0); restyle() }
            "small" -> { small += if (closing) -1 else 1; small = small.coerceAtLeast(0); restyle() }
            // Lists (I1): each item is its own bulleted paragraph, matching
            // `Html.fromHtml(FROM_HTML_MODE_LEGACY)`'s per-`<li>` BulletSpan block. Both the open AND
            // close tag of ul/ol/li force the same block break as p/div do (guarded the same way),
            // so consecutive items separate with exactly one blank line and the bullet is only ever
            // emitted once, on the opening `<li>`.
            "ul", "ol" -> if (lastChar() != null) lineBreak(2)
            "li" -> { if (lastChar() != null) lineBreak(2); if (!closing) sb.append("• ") }
            // Headings (I1): h1-h6 all get the paragraph break + bold `Html.fromHtml` gave them;
            // h1-h3 additionally get the larger size (h4-h6 stay Normal, bold only).
            "h1", "h2", "h3" -> {
                if (lastChar() != null) lineBreak(2)
                bold += if (closing) -1 else if (selfClosing) 0 else 1; bold = bold.coerceAtLeast(0)
                big += if (closing) -1 else if (selfClosing) 0 else 1; big = big.coerceAtLeast(0)
                restyle()
            }
            "h4", "h5", "h6" -> {
                if (lastChar() != null) lineBreak(2)
                bold += if (closing) -1 else if (selfClosing) 0 else 1; bold = bold.coerceAtLeast(0)
                restyle()
            }
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

/**
 * Escapes plain text for use as an `AppDialogRequest.Message`/`Confirm` body, which [parseHtmlRuns]
 * always treats as HTML (I2). Order matters: `&` first (so the entities this inserts are not
 * themselves re-escaped), then `<`/`>` (so a `<` in the source, e.g. from an exception's message,
 * survives as literal text instead of starting a phantom tag), then `\n` -> `<br>` LAST (so the
 * `<`/`>` of the tag this inserts are not escaped back into text). Without this, a plain string
 * posted straight into a dialog body loses every line break (`parseHtmlRuns` collapses `\n` like any
 * other whitespace) and any `<...>` substring is silently dropped as an unknown tag.
 */
fun plainTextToHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
