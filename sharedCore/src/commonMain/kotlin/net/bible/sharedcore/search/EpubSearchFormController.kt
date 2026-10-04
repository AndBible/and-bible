package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Framework-free EPUB search form state. [loadMode]/[saveMode] bridge the host settings key
 * "epubSearch-SearchType" (FTS ↔ persisted null, mapped host-side). [onSubmit] launches results.
 */
class EpubSearchFormController(
    loadMode: () -> EpubSearchMode,
    private val saveMode: (EpubSearchMode) -> Unit,
    private val onSubmit: (query: String, mode: EpubSearchMode) -> Unit,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _mode = MutableStateFlow(loadMode())
    val mode: StateFlow<EpubSearchMode> = _mode.asStateFlow()

    /** Classic `EpubSearch.help()`'s FTS5 query-syntax dialog (platform-dialog removal Task 15),
     *  now this screen's own state -- its text comes entirely from existing `Strings.kt` entries
     *  (`helpSearchEpub`/`helpFts5`/`helpSearchDetails`, already shared with `ReadingToolbar`'s own
     *  search help), so [EpubSearchScreen] renders it directly with no host slot at all. */
    private val _helpOpen = MutableStateFlow(false)
    val helpOpen: StateFlow<Boolean> = _helpOpen.asStateFlow()

    fun setQuery(value: String) { _query.value = value }

    fun setMode(value: EpubSearchMode) { _mode.value = value; saveMode(value) }

    /** Restore a saved mode without re-persisting it (mirrors Plan A's seedTranslations). */
    fun seedMode(value: EpubSearchMode) { _mode.value = value }

    fun submit() { val q = _query.value; if (q.isNotBlank()) onSubmit(q, _mode.value) }

    fun showHelp() { _helpOpen.value = true }
    fun dismissHelp() { _helpOpen.value = false }
}
