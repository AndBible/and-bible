package net.bible.sharedcore.navigation

/**
 * Does a document match a search query? ONE implementation for all three document lists.
 *
 * Semantics, chosen to reproduce what the Room FTS4 path did on the download lists — the query is
 * split on whitespace and EVERY term must prefix-match some token of some field, so terms may land
 * in different fields and their order does not matter, exactly like `MATCH "<query>*"`:
 *
 *   fields: ["KJV", "King James Version", "English", "CrossWire"]
 *   "king james" ✓   "james king" ✓   "jam" ✓   "ames" ✗   "king martin" ✗
 *
 * Two deliberate improvements over the FTS path it replaces. There is **no three-character
 * minimum**, so a two-letter abbreviation is searchable at all. And case folding is Unicode-aware
 * (`lowercase()`), where FTS4's default tokenizer folds ASCII only — so a query starting with "Ä"
 * or a Greek letter no longer silently under-matches.
 *
 * Tokens are maximal runs of letters and digits, which is how the FTS4 simple tokenizer split
 * text too, so "ESV2011" is one token and "Version (Anglicised)" is two.
 *
 * `null` fields are skipped: a screen passes a fixed field list, and a row missing a language or a
 * repository must neither match on it nor blow up.
 */
fun matchesDocumentQuery(query: String, fields: List<String?>): Boolean {
    val terms = query.trim().split(' ', '\t', '\n').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val tokens = fields.filterNotNull().flatMap { tokenizeForSearch(it) }
    if (tokens.isEmpty()) return false
    return terms.all { term ->
        val needle = term.lowercase()
        tokens.any { it.startsWith(needle) }
    }
}

/** Maximal runs of letters and digits, lowercased. Mirrors FTS4's simple tokenizer. */
private fun tokenizeForSearch(text: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    for (ch in text) {
        if (ch.isLetterOrDigit()) sb.append(ch)
        else if (sb.isNotEmpty()) { out.add(sb.toString().lowercase()); sb.clear() }
    }
    if (sb.isNotEmpty()) out.add(sb.toString().lowercase())
    return out
}
