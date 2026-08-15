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
    if (runs.isEmpty()) runs.add(StyledRun(""))
    return StyledText(runs)
}
