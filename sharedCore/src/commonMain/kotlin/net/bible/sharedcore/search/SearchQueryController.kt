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
    private val loadRecentTerms: () -> List<String> = { emptyList() },
    private val maxRecentTerms: Int = 10,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recentTerms = MutableStateFlow(loadRecentTerms())
    val recentTerms: StateFlow<List<String>> = _recentTerms.asStateFlow()

    fun setQuery(v: String) { _query.value = v }

    /**
     * Re-reads the persisted MRU into [recentTerms].
     *
     * The list is loaded once at construction, which is right for a screen that is created per
     * search — but the reading-view host lives as long as the activity, and the SAME store is
     * written by the classic/EPUB search Activities. Without this, a term recorded there while the
     * host was alive is clobbered the next time the host records one of its own (it would persist
     * its stale in-memory list). Called per search-open, next to the other per-open refreshes.
     */
    fun reloadRecentTerms() { _recentTerms.value = loadRecentTerms() }

    /** Most-recent-first, de-duplicated, trimmed, capped. Blank terms are ignored. */
    fun recordRecentTerm(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val updated = (listOf(t) + _recentTerms.value.filter { it != t }).take(maxRecentTerms)
        _recentTerms.value = updated
        persistRecentTerms(updated)
    }
}
