/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedcore.search


fun prepareSearchTerms(searchTerms_: String): String {
    // Replaces strong:g00123 with REGEX strong:g*123. This is needed because the search term submitted by the 'Find all occcurrences includes extra zeros
    // The capitalisation is not important since we do a case insensitive search
    var searchTerms = searchTerms_
    if (searchTerms.contains("strong:")) {
        searchTerms = searchTerms.replace("strong:g0*".toRegex(), "strong:g0*")
        searchTerms = searchTerms.replace("strong:h0*".toRegex(), "strong:h0*")
    }
    return searchTerms
}

val splitSearchTermsRegex = Regex("""\s+(?=(?:"(?:\\"|[^"])+"|[^"])+$)""")

// Split the search terms on space characters that are not enclosed in double quotes
// Eg: 'moses "burning bush"' -> "moses" and "burning bush"
fun splitSearchTerms(searchTerms: String): List<String> = splitSearchTermsRegex.split(searchTerms)

/**
 * Regex sources (compile case-insensitively) that match the `lemma` attribute of a word carrying one of the
 * positive Strong's terms of [lemmaQuery], one pattern per `strong:` token.
 *
 * [lemmaQuery] may be the whole decorated query (`+strong:h0*7225 "strong:g0*2316" -strong:h0*430`): tokens
 * are split outside quotes, quotes and a leading `+` are dropped, `-` (excluded) and non-Strong's tokens are
 * ignored. The number is matched with optional leading zeros (`0*`) and a trailing `\b`, so `strong:h0*72`
 * does not match H7225; only a `*` at the very END of the token is the user's wildcard (`\w*`), a `?` is `\w`.
 */
fun strongsHighlightPatterns(lemmaQuery: String): List<String> =
    splitSearchTerms(lemmaQuery.trim()).mapNotNull { raw ->
        val token = raw.replace("\"", "").removePrefix("+")
        if (!token.startsWith("strong:", ignoreCase = true)) return@mapNotNull null
        val body = token.substring("strong:".length)
        if (body.isEmpty()) return@mapNotNull null
        val prefix = body[0]
        val prefixSource = if (prefix.lowercaseChar() == 'h' || prefix.lowercaseChar() == 'g') "[hg]" else Regex.escape(prefix.toString())
        val number = body.substring(1).replace(Regex("^(?:0\\*|0)+"), "")
        val source = StringBuilder("strong:").append(prefixSource).append("0*")
        number.forEachIndexed { i, c ->
            when {
                c == '*' && i == number.lastIndex -> source.append("\\w*")
                c == '?' -> source.append("\\w")
                else -> source.append(Regex.escape(c.toString()))
            }
        }
        source.append("\\b").toString()
    }

fun prepareSearchWord(searchWord_: String): String {
    // Need to clean up the search word itself before trying to find the searchWord in the text
    // Eg: '+"burning bush"' -> 'burning bush'
    var searchWord = searchWord_
    searchWord =
        searchWord.replace("\"", "") // Remove quotes which indicate phrase searches
    searchWord = searchWord.replace("+", "") // Remove + which indicates AND searches
    searchWord = searchWord.replace("?", "\\p{L}") // Handles any letter from any language
    if (searchWord.length > 0) {
        searchWord = if (searchWord.substring(searchWord.length - 1) == "*") {
            searchWord.replace("*", "")
        } else {
            searchWord.replace("*", "\b") // Match on a word boundary
        }
    }
    return searchWord
}

/**
 * True for characters of scripts written without spaces between words (Thai, Lao, Myanmar, Khmer, Hiragana,
 * Katakana, Han). Code point ranges, because `Character.UnicodeScript` is not available in common code.
 */
internal fun isUnspacedScript(c: Char): Boolean = c.code.let {
    it in 0x0E00..0x0EFF || it in 0x1000..0x109F || it in 0x1780..0x17FF ||
        it in 0x3040..0x30FF || it in 0x3400..0x4DBF || it in 0x4E00..0x9FFF || it in 0xF900..0xFAFF
}

/** The same ranges as [isUnspacedScript], as the inside of a regex character class. */
private const val UNSPACED_CLASS =
    "\\u0E00-\\u0EFF\\u1000-\\u109F\\u1780-\\u17FF\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF"

/**
 * Regex source (compile case-insensitively) that highlights [originalTerm] in result previews, or `null` when
 * the term is empty after [prepareSearchWord].
 *
 * A term containing an unspaced-script character (Thai, CJK, ...) is matched bare: there is no `\b` inside a run
 * of such letters, and Thai combining marks would create boundaries in the middle of a word. Other terms keep
 * word-boundary matching (`\b$word\b`, or `\b$word[\w'\-]*\b` for a `*` wildcard); a boundary also counts next
 * to an unspaced-script character, so `God` is found in `God上帝` but not in `Godhead上帝`.
 */
fun termHighlightPattern(originalTerm: String): String? {
    val word = prepareSearchWord(originalTerm)
    if (word.isEmpty()) return null
    if (word.any { isUnspacedScript(it) }) return word
    val start = "(?:\\b|(?<=[$UNSPACED_CLASS]))"
    val end = "(?:\\b|(?=[$UNSPACED_CLASS]))"
    return if (originalTerm.contains("*")) {
        "$start$word(?:(?![$UNSPACED_CLASS])[\\w'\\-])*$end"
    } else {
        "$start$word$end"
    }
}

/** Longest search result excerpt (in characters, ellipses excluded) cut from a long non-Bible entry. */
const val SEARCH_EXCERPT_LENGTH: Int = 200

private val whitespaceRun = Regex("""\s+""")

/**
 * At most [maxLength] characters of [text] around the first match of any of [terms] (the user's search words, as
 * passed to [termHighlightPattern]), cut at word boundaries and marked with `…` where text was left out. Whitespace
 * runs collapse to one space (as the rendered preview shows them).
 *
 * The match is first looked for literally (as the highlighter does). The index matches more than that, though: it
 * ignores case and accents and stems words ("vines" finds "vine"), so when no term matches literally, a word that
 * matches a term case- and accent-insensitively, by stem, is used. Only without any match is the excerpt the start.
 */
fun searchExcerpt(text: String, terms: List<String>, maxLength: Int = SEARCH_EXCERPT_LENGTH): String {
    val plain = text.replace(whitespaceRun, " ").trim()
    if (plain.length <= maxLength) return plain
    val literal = terms.asSequence()
        .mapNotNull { termHighlightPattern(it) }
        .mapNotNull { pattern -> runCatching { Regex(pattern, RegexOption.IGNORE_CASE).find(plain)?.range }.getOrNull() }
        .minByOrNull { it.first }
    val match = literal ?: firstStemMatch(plain, terms)
    return cutExcerpt(plain, match?.first ?: 0, match?.let { it.last + 1 } ?: 0, maxLength)
}

/**
 * Like [searchExcerpt], around the first `<b>…</b>` run: the highlighters mark the lemma matches of a Strong's search
 * so, and there are no search words to look for in the text. The start of the text when it has no such run.
 */
fun searchExcerptAtBold(text: String, maxLength: Int = SEARCH_EXCERPT_LENGTH): String {
    val plain = text.replace(whitespaceRun, " ").trim()
    if (plain.length <= maxLength) return plain
    val open = plain.indexOf("<b>")
    if (open < 0) return cutExcerpt(plain, 0, 0, maxLength)
    val close = plain.indexOf("</b>", open)
    return cutExcerpt(plain, open, if (close < 0) open + 3 else close + 4, maxLength)
}

/** The range of the first word of [plain] that one of [terms] matches by stem, ignoring case and accents. */
private fun firstStemMatch(plain: String, terms: List<String>): IntRange? {
    val stems = terms.flatMap { term -> wordRuns(term).map { foldForMatch(it) } }
        .filter { it.length >= MIN_STEM_WORD }
        .map { it to stemOf(it) }
    if (stems.isEmpty()) return null
    val folded = foldForMatch(plain)
    var i = 0
    while (i < folded.length) {
        if (!isWordChar(folded[i])) { i++; continue }
        var j = i
        while (j < folded.length && isWordChar(folded[j])) j++
        val word = folded.substring(i, j)
        if (word.length >= MIN_STEM_WORD && stems.any { (term, stem) -> word.startsWith(stem) || term.startsWith(word) }) {
            return i until j
        }
        i = j
    }
    return null
}

private const val MIN_STEM_WORD = 3

private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '-'

private fun wordRuns(term: String): List<String> =
    term.split(Regex("[^\\p{L}\\p{N}'\\-]+")).filter { it.isNotEmpty() }

/** The term without an inflectional ending: one trailing character, two for a long term ("vines" -> "vine"). */
private fun stemOf(term: String): String = term.dropLast(if (term.length >= 6) 2 else if (term.length >= 4) 1 else 0)

private val accentGroups = mapOf(
    'a' to "àáâãäåāăą", 'c' to "çćč", 'd' to "ďđ", 'e' to "èéêëēėęě", 'g' to "ğ", 'i' to "ìíîïīįı", 'l' to "ł",
    'n' to "ñńň", 'o' to "òóôõöøōő", 'r' to "ŕř", 's' to "śšş", 't' to "ť", 'u' to "ùúûüūůűų", 'y' to "ýÿ", 'z' to "źżž",
)
private val accentFold: Map<Char, Char> = accentGroups.flatMap { (base, accented) -> accented.map { it to base } }.toMap()

/** Lower case with accents removed, one character for one: indexes in the result are indexes in [s]. */
private fun foldForMatch(s: String): String {
    val out = StringBuilder(s.length)
    for (c in s) {
        val lower = c.lowercaseChar()
        out.append(accentFold[lower] ?: lower)
    }
    return out.toString()
}

/**
 * [maxLength] characters of [plain] around the match at [matchStart] until [matchEnd], cut at spaces where there is
 * one, and never in the middle of a surrogate pair.
 */
private fun cutExcerpt(plain: String, matchStart: Int, matchEnd: Int, maxLength: Int): String {
    var start = (matchStart - maxLength / 3).coerceAtLeast(0).coerceAtMost(plain.length - maxLength)
    if (start > 0 && plain[start - 1] != ' ') {
        val space = plain.indexOf(' ', start)
        if (space in 0 until matchStart) start = space + 1
    }
    var end = (start + maxLength).coerceAtMost(plain.length)
    if (end < plain.length && plain[end] != ' ') {
        val space = plain.lastIndexOf(' ', end - 1)
        if (space > start && space >= matchEnd) end = space
    }
    if (start > 0 && plain[start].isLowSurrogate() && plain[start - 1].isHighSurrogate()) start++
    if (end < plain.length && plain[end].isLowSurrogate() && plain[end - 1].isHighSurrogate()) end--
    val excerpt = plain.substring(start, end).trim()
    return (if (start > 0) "…" else "") + excerpt + (if (end < plain.length) "…" else "")
}
