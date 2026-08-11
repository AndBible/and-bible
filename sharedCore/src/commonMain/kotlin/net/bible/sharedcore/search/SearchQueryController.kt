package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The query text and the recent-terms MRU — the only form state SWORD and EPUB search genuinely share.
 * Held by composition rather than inheritance so each form controller keeps its own type-specific state
 * and neither carries the other's dead fields (spec §5: divergence pressure is one-directional).
 *
 * Persistence is injected: the store lives host-side (`CommonUtils.settings`, newline-separated because
 * a query may contain commas).
 */
class SearchQueryController(
    private val persistRecentTerms: (List<String>) -> Unit = {},
    loadRecentTerms: () -> List<String> = { emptyList() },
    private val maxRecentTerms: Int = 10,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recentTerms = MutableStateFlow(loadRecentTerms())
    val recentTerms: StateFlow<List<String>> = _recentTerms.asStateFlow()

    fun setQuery(v: String) { _query.value = v }

    /** Most-recent-first, de-duplicated, trimmed, capped. Blank terms are ignored. */
    fun recordRecentTerm(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val updated = (listOf(t) + _recentTerms.value.filter { it != t }).take(maxRecentTerms)
        _recentTerms.value = updated
        persistRecentTerms(updated)
    }
}
