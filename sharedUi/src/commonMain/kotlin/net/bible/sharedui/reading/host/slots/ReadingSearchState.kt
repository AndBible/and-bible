@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.search.ReadingSearchRequest
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.ReadingSearchBarCallbacks

/**
 * Routes reading Find to its owning window. A session includes its result controllers, list position,
 * settings and index continuation, not just its query. Stable outer flows keep host/scaffold consumers
 * subscribed while the active session changes; Compose slot reads observe [session] directly.
 */
@kotlin.native.HiddenFromObjC
class ReadingSearchState(
    private val settings: AppSettings,
    val scope: CoroutineScope,
    internal val platform: ReadingSearchPlatform,
    private val indexJobs: SearchIndexJobs,
    private val services: ReadingSearchServices,
    internal val onLeaveFullScreen: () -> Unit,
    private val resultsScope: CoroutineScope = scope,
    private val pollPause: suspend () -> Unit = { delay(SEARCH_INDEX_POLL_INTERVAL_MS) },
    private val indexValidationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val windowState: WindowStateService? = null,
) {
    private data class OwnedSession(val state: ReadingSearchSession, val lifetime: Job, val resultLifetime: Job)
    private val sessions = mutableMapOf<String, OwnedSession>()

    private fun createSession(id: String): OwnedSession {
        val lifetime = SupervisorJob(scope.coroutineContext[Job])
        val sessionScope = CoroutineScope(scope.coroutineContext + lifetime)
        val resultLifetime = SupervisorJob(resultsScope.coroutineContext[Job])
        val resultScope = CoroutineScope(resultsScope.coroutineContext + resultLifetime)
        return OwnedSession(
            ReadingSearchSession(settings, sessionScope, platform.forWindow(id), indexJobs, services,
                onLeaveFullScreen, resultScope, pollPause, indexValidationDispatcher),
            lifetime, resultLifetime,
        )
    }

    private var activeId = windowState?.layout?.value?.activeWindowId.orEmpty()
    internal var session by mutableStateOf(sessions.getOrPut(activeId) { createSession(activeId) }.state)
        private set
    private val activeSession = MutableStateFlow(session)

    private fun <T> follow(initial: T, read: (ReadingSearchSession) -> StateFlow<T>): StateFlow<T> =
        activeSession.flatMapLatest(read).stateIn(scope, SharingStarted.Eagerly, initial)

    val searchBar = follow<ReadingSearchBarState?>(null) { it.searchBar }
    val searchSheetVisible = follow(false) { it.searchSheetVisible }
    val searchFieldFocused = follow(false) { it.searchFieldFocused }
    val searchUnavailableDocName = follow<String?>(null) { it.searchUnavailableDocName }

    private fun discard(id: String) {
        sessions.remove(id)?.let {
            it.state.leaveSearch()
            it.state.dispose()
            it.resultLifetime.cancel()
            it.lifetime.cancel()
        }
    }

    /** Called synchronously by host window events before forwarding any document-change event. */
    fun activeWindowChanged() {
        windowState?.layout?.value?.let(::applyLayout)
    }

    private fun applyLayout(layout: net.bible.sharedcore.window.WindowLayoutState) {
        val liveIds = layout.windows.filter { it.state != WindowStateValue.CLOSED }.map { it.id }.toSet()
        sessions.keys.toList().filter { it !in liveIds && it != "" }.forEach(::discard)
        if (activeId != layout.activeWindowId || sessions[activeId]?.state !== session) {
            session.searchFieldFocused.value = false
            session.searchRecentMenuOpen.value = false
            session.searchSettingsOpen.value = false
            // Returning restores content, not keyboard ownership in a WebView editor.
            session.controller.imeRequestHandled()
            activeId = layout.activeWindowId
            session = sessions.getOrPut(activeId) { createSession(activeId) }.state
            session.controller.imeRequestHandled()
            activeSession.value = session
        }
    }

    private val windowSubscription = windowState?.let { windows ->
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            windows.layout.collect(::applyLayout)
        }
    }

    val controller get() = session.controller
    val queries get() = session.queries
    val searchType get() = session.searchType
    val searchSection get() = session.searchSection
    val searchTranslations get() = session.searchTranslations
    val searchAvailableTranslations get() = session.searchAvailableTranslations
    val searchSettingsOpen get() = session.searchSettingsOpen
    val searchRecentMenuOpen get() = session.searchRecentMenuOpen
    val searchResultsExpanded get() = session.searchResultsExpanded
    val searchResultsListState get() = session.searchResultsListState
    val searchResults get() = session.searchResults
    val epubSearchResults get() = session.epubSearchResults
    val epubSearchMode get() = session.epubSearchMode
    val searchIndexProgress get() = session.searchIndexProgress
    val openResultInLinksWindow get() = session.openResultInLinksWindow
    val searchSelectorPendingIds get() = session.searchSelectorPendingIds
    val lastSearchRequestForTest get() = session.lastSearchRequestForTest
    internal val searchIndexService get() = session.searchIndexService

    // The host holds these callbacks for its lifetime. Resolve the owner at invocation, not when
    // building the toolbar, so the second window cannot accidentally edit the first one's query.
    val searchBarCallbacks = ReadingSearchBarCallbacks(
        onQueryChange = { session.searchBarCallbacks.onQueryChange(it) },
        onSubmit = { session.searchBarCallbacks.onSubmit() },
        onRecentTermsOpen = { session.searchBarCallbacks.onRecentTermsOpen() },
        onRecentTermsDismiss = { session.searchBarCallbacks.onRecentTermsDismiss() },
        onRecentTermSelected = { session.searchBarCallbacks.onRecentTermSelected(it) },
        onOpenSettings = { session.searchBarCallbacks.onOpenSettings() },
        onClose = { leaveSearch() },
        onImeRequestHandled = { session.searchBarCallbacks.onImeRequestHandled() },
        onFieldFocusChanged = { session.searchBarCallbacks.onFieldFocusChanged(it) },
        onRebuildIndex = { session.searchBarCallbacks.onRebuildIndex() },
        onShowResults = { session.searchBarCallbacks.onShowResults() },
    )

    fun openSearch(seedQuery: String? = null, preDecorated: Boolean = false, fromShortcut: Boolean = false) =
        session.openSearch(seedQuery, preDecorated, fromShortcut)
    fun openSearchStrongs(ref: String, translationIds: List<String>) = session.openSearchStrongs(ref, translationIds)
    fun openRequest(request: ReadingSearchRequest, onAccepted: () -> Unit = {}) = session.openRequest(request, onAccepted)
    fun closeSearchIfOpen() = session.closeSearchIfOpen()
    fun leaveSearch() = session.leaveSearch()
    fun setSearchTranslations(ids: List<String>) = session.setSearchTranslations(ids)
    fun loadPersistedTranslations(installed: Set<String>) = session.loadPersistedTranslations(installed)
    fun setOpenResultInLinksWindow(value: Boolean) = session.setOpenResultInLinksWindow(value)
    fun persistEpubSearchMode(mode: EpubSearchMode) = session.persistEpubSearchMode(mode)
    fun reloadEpubSearchMode() = session.reloadEpubSearchMode()
    fun searchUnavailableMessageShown() = session.searchUnavailableMessageShown()
    fun buildSearchRequest(docId: String, query: String) = session.buildSearchRequest(docId, query)
    fun runSearch(docId: String, query: String) = session.runSearch(docId, query)
    fun runEpubSearch(docId: String, query: String) = session.runEpubSearch(docId, query)
    fun onEpubSearchResultSelected(keyId: String, ordinal: Int) = session.onEpubSearchResultSelected(keyId, ordinal)
    fun onSearchResultSelected(referenceName: String, translationId: String?) = session.onSearchResultSelected(referenceName, translationId)
    fun openSearchResultsInWindow() = session.openSearchResultsInWindow()
    fun onSearchTranslationsChosen(ids: List<String>) = session.onSearchTranslationsChosen(ids)
    fun searchSettingsForEpub() = session.searchSettingsForEpub()
    fun currentBookName() = session.currentBookName()

    fun dispose() {
        windowSubscription?.cancel()
        sessions.keys.toList().forEach(::discard)
    }
}
