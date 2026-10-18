package net.bible.sharedlogic.text

import net.bible.service.sword.HtmlToPlainText

/**
 * Common [HtmlToPlainText] for platforms without `android.text.Html` (iOS). Follows what
 * `htmlToSpan(html).toString()` gives for note and verse HTML under `Html.FROM_HTML_MODE_LEGACY`:
 * tags are dropped, `<br>` is a newline, and `<p>` and every other block (`div`, `li`, headings, `blockquote`, ...)
 * is surrounded by a blank line (two newlines, never more, none at the start of the text), runs of whitespace collapse to one space, a space after
 * a space or newline (or at the start) is dropped, comments and `script`/`style` content are dropped, and entities
 * are decoded with [HtmlEntities] (`&nbsp;` stays U+00A0, as on Android).
 */
object CommonHtmlToPlainText : HtmlToPlainText {
    private val BLOCKS = setOf(
        "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "table", "tr", "section",
        "article", "header", "footer", "dl", "dt", "dd",
    )

    override fun convert(html: String): String {
        val out = StringBuilder()
        var text = StringBuilder()
        var pOpen = false

        fun flushText() {
            if (text.isEmpty()) return
            val decoded = HtmlEntities.unescapeHtml4(text.toString())
            text = StringBuilder()
            // Whitespace collapsing applies to the raw characters; entities such as &nbsp; are not whitespace.
            appendCollapsed(out, decoded)
        }

        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c != '<') { text.append(c); i++; continue }
            if (html.startsWith("<!--", i)) {
                val end = html.indexOf("-->", i + 4)
                i = if (end < 0) html.length else end + 3
                continue
            }
            val close = html.indexOf('>', i + 1)
            val next = if (i + 1 < html.length) html[i + 1] else ' '
            if (close < 0 || !(next.isLetter() || next == '/')) { text.append(c); i++; continue }
            val tag = html.substring(i + 1, close)
            val closing = tag.startsWith("/")
            val name = tag.removePrefix("/").trimStart().takeWhile { it.isLetterOrDigit() }.lowercase()
            flushText()
            i = close + 1
            when {
                !closing && (name == "script" || name == "style") -> {
                    val end = html.indexOf("</$name", i, ignoreCase = true)
                    val endClose = if (end < 0) -1 else html.indexOf('>', end)
                    i = if (endClose < 0) html.length else endClose + 1
                }
                name == "br" -> out.append('\n')
                // TagSoup auto-closes an open <p> at the next <p> and ignores an end tag that has no open <p>.
                name == "p" && closing -> if (pOpen) { pOpen = false; ensureTrailingNewlines(out, 2) }
                name == "p" -> { pOpen = true; ensureTrailingNewlines(out, 2) }
                name in BLOCKS -> ensureTrailingNewlines(out, 2)
            }
        }
        flushText()
        return out.toString()
    }

    /** Legacy `Html` ends a paragraph or block with up to [count] newlines, none at the very start of the text. */
    private fun ensureTrailingNewlines(out: StringBuilder, count: Int) {
        if (out.isEmpty()) return
        var have = 0
        while (have < count && have < out.length && out[out.length - 1 - have] == '\n') have++
        repeat(count - have) { out.append('\n') }
    }

    private fun appendCollapsed(out: StringBuilder, s: String) {
        for (ch in s) {
            if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r') {
                val last = if (out.isEmpty()) '\n' else out[out.length - 1]
                if (last != ' ' && last != '\n') out.append(' ')
            } else {
                out.append(ch)
            }
        }
    }
}
