package net.bible.service.format

/** Decodes the HTML fragments in SAX character data (not complete styled HTML documents). */
fun decodeHtmlEntities(s: String): String {
    val out = StringBuilder()
    fun text(value: String) {
        for (c in value) {
            // Android collapses only ASCII spaces and LF, not tabs or NBSP.
            if (c == ' ' || c == '\n') {
                if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
            } else out.append(if (c.code in 0x80..0x9f) windowsCodePoints[c.code - 0x80].toChar() else c)
        }
    }
    val pending = StringBuilder()
    fun flushText() {
        // TagSoup ignores all-whitespace scanner chunks before body text starts. Entity/tag
        // boundaries flush chunks, so a leading thin space survives only beside real text.
        if (out.isNotEmpty() || pending.any { !isScannerWhitespace(it) }) text(pending.toString())
        pending.clear()
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
                flushText()
                val end = s.indexOf("-->", i + 4)
                i = if (end < 0) s.length else end + 3
            }
            s[i] == '<' && i + 1 < s.length && (s[i + 1].isLetterOrDigit() || s[i + 1] == '/' || s[i + 1] == '!') -> {
                flushText()
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
                flushText()
                val match = entityAtStart.find(s, i)?.takeIf { it.range.first == i }
                if (match == null) {
                    pending.append("&")
                    i++
                } else {
                    val name = match.groupValues[1]
                    val numeric = name.startsWith('#')
                    val code = if (numeric) {
                        if (name.startsWith("#x", ignoreCase = true)) name.substring(2).toIntOrNull(16)
                        else name.substring(1).toIntOrNull()
                    } else htmlEntityCodePoints[name]
                    if (code == null || code !in 1..0x10ffff) pending.append(match.value) else {
                        // The scanner drops resolved controls (including named Tab/NewLine).
                        // Raw and resolved C1 still map through Windows-1252.
                        if (code >= 32) pending.append(codePointText(code))
                    }
                    i += match.value.length
                }
            }
            else -> {
                // The HTML scanner normalizes CR and CRLF to one LF before whitespace collapse.
                if (s[i] == '\r') {
                    pending.append("\n")
                    i += if (i + 1 < s.length && s[i + 1] == '\n') 2 else 1
                } else {
                    pending.append(s[i])
                    i++
                }
            }
        }
    }
    flushText()
    return out.toString()
}

// Portable equivalent of the framework parser's whitespace classification; NBSP is not whitespace.
private fun isScannerWhitespace(c: Char): Boolean = c.code in 9..13 || c.code in 0x1c..0x20 ||
    c.code == 0x1680 || c.code in 0x2000..0x2006 || c.code in 0x2008..0x200a ||
    c.code == 0x2028 || c.code == 0x2029 || c.code == 0x205f || c.code == 0x3000

private fun codePointText(code: Int): String = if (code <= 0xffff) code.toChar().toString() else {
    val supplementary = code - 0x10000
    "${(0xd800 + (supplementary shr 10)).toChar()}${(0xdc00 + (supplementary and 0x3ff)).toChar()}"
}

// TagSoup's Windows table replaces the five undefined C1 slots with U+FFFD.
private val windowsCodePoints = intArrayOf(
    0x20ac, 0xfffd, 0x201a, 0x0192, 0x201e, 0x2026, 0x2020, 0x2021,
    0x02c6, 0x2030, 0x0160, 0x2039, 0x0152, 0xfffd, 0x017d, 0xfffd,
    0xfffd, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2013, 0x2014,
    0x02dc, 0x2122, 0x0161, 0x203a, 0x0153, 0xfffd, 0x017e, 0x0178,
)

private val entityAtStart = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[a-zA-Z][a-zA-Z0-9]*);?")

/** Selected at the Android composition boundary; API 23 predates the modern Html path. */
enum class SaxTextPolicy {
    MODERN, LEGACY;

    val restoreLeadingSpace: Boolean get() = this == MODERN
    fun canonical(text: String): String = if (this == MODERN) decodeHtmlEntities(text) else text
    fun speech(text: String): String = if (this == MODERN) decodeHtmlEntities(text) else text.replace("&quot;", "\"")
}
