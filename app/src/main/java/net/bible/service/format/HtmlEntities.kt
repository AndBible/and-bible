package net.bible.service.format

import org.apache.commons.text.StringEscapeUtils

/** Decodes the HTML fragments in SAX character data (not complete styled HTML documents). */
fun decodeHtmlEntities(s: String): String {
    val out = StringBuilder()
    fun text(value: String) {
        for (c in value) {
            // Android collapses only ASCII spaces and LF, not tabs or NBSP.
            if (c == ' ' || c == '\n') {
                if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
            } else out.append(c)
        }
    }
    fun paragraph() {
        if (out.isNotEmpty()) {
            if (out.last() != '\n') out.append('\n')
            if (out.length < 2 || out[out.length - 2] != '\n') out.append('\n')
        }
    }
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("<!--", i) -> {
                val end = s.indexOf("-->", i + 4)
                i = if (end < 0) s.length else end + 3
            }
            s[i] == '<' && i + 1 < s.length && (s[i + 1].isLetterOrDigit() || s[i + 1] == '/' || s[i + 1] == '!') -> {
                val end = s.indexOf('>', i + 1)
                if (end < 0) break // TagSoup discards an unfinished tag, e.g. "1 <2".
                val tag = s.substring(i + 1, end).trim().trimStart('/').takeWhile { it.isLetterOrDigit() }.lowercase()
                when (tag) {
                    "br" -> out.append('\n')
                    "p", "div", "blockquote", "ul", "li", "h1", "h2", "h3", "h4", "h5", "h6" -> paragraph()
                }
                i = end + 1
            }
            s[i] == '&' -> {
                val match = entityAtStart.find(s, i)?.takeIf { it.range.first == i }
                if (match == null) {
                    text("&")
                    i++
                } else {
                    val name = match.groupValues[1]
                    val token = "&$name;"
                    val decoded = if (name == "apos") "'" else StringEscapeUtils.unescapeHtml4(token)
                    if (decoded == token) text(match.value) else {
                        // TagSoup drops control-code numeric references before Html sees them.
                        text(if (name.startsWith('#')) decoded.filter { it >= ' ' } else decoded)
                    }
                    i += match.value.length
                }
            }
            else -> {
                text(if (s[i] == '\r') "" else s[i].toString())
                i++
            }
        }
    }
    return out.toString()
}

private val entityAtStart = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[a-zA-Z][a-zA-Z0-9]*);?")

/** Selected at the Android composition boundary; API 23 predates the modern Html path. */
enum class SaxTextPolicy {
    MODERN, LEGACY;

    val restoreLeadingSpace: Boolean get() = this == MODERN
    fun canonical(text: String): String = if (this == MODERN) decodeHtmlEntities(text) else text
    fun speech(text: String): String = if (this == MODERN) decodeHtmlEntities(text) else text.replace("&quot;", "\"")
}
