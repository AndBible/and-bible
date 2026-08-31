package net.bible.sharedcore.search

/** EPUB FTS5 query mode. FTS = the user typed a raw FTS5 expression (classic `ftsQuery` radio → null). */
enum class EpubSearchMode { ALL_WORDS, ANY_WORD, PHRASE, FTS }

/**
 * One EPUB search hit. [keyId] is the stable `BookAndKey` id addressing the FRAGMENT
 * (`"<initials>:<fragmentId>"`); [ordinal] is the hit's position WITHIN that fragment, which the
 * FTS5 index stores per `BVA` element. The pair is what identifies a hit: a fragment can contain
 * several hits, so [keyId] alone is not unique — using it as a list key crashed the reading view
 * (F44/B1). [rowId] is that pair, for list keys; navigation uses both parts separately.
 */
data class EpubResultRow(
    val keyId: String,
    val ordinal: Int,
    val keyName: String,
    val text: StyledText,
) {
    val rowId: String get() = "$keyId#$ordinal"
}

/** Port of classic EpubSearchResults.adjustSearchText — mode → FTS5 query syntax. */
fun adjustSearchText(mode: EpubSearchMode, text: String): String = when (mode) {
    EpubSearchMode.PHRASE -> "\"$text\""
    EpubSearchMode.ALL_WORDS -> text.split(' ').joinToString(" AND ")
    EpubSearchMode.ANY_WORD -> text.split(' ').joinToString(" OR ")
    EpubSearchMode.FTS -> text
}

/**
 * Parse the FTS5 highlight() output (only `<b>…</b>` bold tags, plus HTML entities) into a portable
 * StyledText. The classic EPUB adapter fed this HTML to htmlToSpan; here it becomes StyledRuns so the
 * shared mapper renders it as an AnnotatedString (iOS-clean — no android.text).
 */
fun parseHighlightHtml(html: String): StyledText {
    val runs = mutableListOf<StyledRun>()
    var i = 0
    var bold = false
    val buf = StringBuilder()
    fun flush() { if (buf.isNotEmpty()) { runs.add(StyledRun(buf.toString(), bold = bold)); buf.clear() } }
    while (i < html.length) {
        when {
            html.startsWith("<b>", i) -> { flush(); bold = true; i += 3 }
            html.startsWith("</b>", i) -> { flush(); bold = false; i += 4 }
            html[i] == '&' -> {
                val semi = html.indexOf(';', i)
                if (semi > i) {
                    when (html.substring(i, semi + 1)) {
                        "&amp;" -> buf.append('&'); "&lt;" -> buf.append('<')
                        "&gt;" -> buf.append('>'); "&quot;" -> buf.append('"')
                        "&#39;", "&apos;" -> buf.append('\'')
                        else -> buf.append(html, i, semi + 1)
                    }
                    i = semi + 1
                } else { buf.append(html[i]); i++ }
            }
            else -> { buf.append(html[i]); i++ }
        }
    }
    flush()
    return normalizeWhitespace(runs)
}

/**
 * HTML whitespace semantics, applied after parsing: any run of ASCII whitespace becomes ONE space,
 * across styled-run boundaries as well as inside a run, and the whole text is trimmed at both ends.
 *
 * The indexed text is the source XHTML's character data (`EpubBackendState.buildSearchIndex` indexes
 * JDOM's `Element.text`), so it carries that file's line breaks and indentation — which a browser
 * collapses and a `Text` composable does not. Doing it here rather than at index time is deliberate:
 * it fixes EXISTING indexes, and the FTS5 tokenizer never cared about the whitespace anyway.
 *
 * Non-breaking space is left alone — it is content. `Char.isWhitespace()` is avoided for exactly
 * that reason: its answer for U+00A0 is platform-dependent, and this module compiles for iOS too.
 */
private fun normalizeWhitespace(runs: List<StyledRun>): StyledText {
    fun isCollapsible(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C'
    val out = mutableListOf<StyledRun>()
    var lastWasSpace = true // true at the start, so leading whitespace is dropped
    for (run in runs) {
        val sb = StringBuilder()
        for (c in run.text) {
            if (isCollapsible(c)) {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true }
            } else {
                sb.append(c); lastWasSpace = false
            }
        }
        if (sb.isNotEmpty()) out.add(StyledRun(sb.toString(), bold = run.bold, highlight = run.highlight))
    }
    // A single trailing space can only be the last run's last character, by construction above.
    val last = out.lastOrNull()
    if (last != null && last.text.endsWith(' ')) {
        val trimmed = last.text.dropLast(1)
        out[out.size - 1] = last.copy(text = trimmed)
        if (trimmed.isEmpty()) out.removeAt(out.size - 1)
    }
    if (out.isEmpty()) out.add(StyledRun(""))
    return StyledText(out)
}
