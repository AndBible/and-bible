package net.bible.sharedui.reading.host.slots

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.cancel
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.window.WindowStateValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertNull

class ReadingSearchWindowsTest {
    private class Windows : WindowStateService {
        override val layout = MutableStateFlow(snapshot("a"))
        fun activate(id: String) { layout.value = snapshot(id) }
        fun close(id: String) { layout.value = layout.value.copy(windows = layout.value.windows.filterNot { it.id == id }) }
        companion object {
            fun snapshot(active: String) = WindowLayoutState(
                windows = listOf("a", "b").map { WindowSnapshot(it, WindowStateValue.VISIBLE, 1f, true, false, false, 0, false) },
                activeWindowId = active, maximizedWindowId = null, reverseSplitMode = false, restoreButtonsVisible = true,
            )
        }
    }

    @Test fun backgroundIndexCompletionKeepsTheOwnersDocumentAndBookSection() {
        val fixture = SearchFixture()
        val windows = Windows()
        val books = mapOf("a" to fakeSearchBook("BOOK_A"), "b" to fakeSearchBook("BOOK_B"))
        books.values.forEach { fixture.platform.documents[it.initials] = it }
        fixture.platform.documentInfos.putAll(books.values.associate { book ->
            book.initials to net.bible.sharedcore.search.SearchDocumentInfo(book.initials,
                net.bible.sharedcore.search.SearchDocumentCategory.BIBLE, false, false)
        })
        val platform = object : ReadingSearchPlatform by fixture.platform {
            override fun forWindow(windowId: String): ReadingSearchPlatform = object : ReadingSearchPlatform by fixture.platform {
                override fun currentDocument() = books[windowId]
                override fun currentBookName() = if (windowId == "a") "Genesis" else "John"
            }
        }
        val state = ReadingSearchState(fixture.settings, fixture.scope, platform, fixture.jobs,
            ReadingSearchServices(fixture.bible, FakeEpubSearch(), net.bible.sharedcore.search.SearchResultsCache(), fixture.index),
            onLeaveFullScreen = {}, windowState = windows)
        try {
            state.openSearch()
            state.queries.setQuery("light")
            state.controller.acceptIndexing()
            val owner = state.controller
            windows.activate("b")
            state.openSearch()
            fixture.platform.documentInfos["BOOK_A"] = fixture.platform.documentInfos.getValue("BOOK_A").copy(indexDone = true)
            owner.onIndexingFinished(true)
            assertEquals(net.bible.sharedcore.search.ReadingSearchPhase.NeedsIndex("BOOK_B", false), state.controller.phase.value)
            windows.activate("a")
            assertEquals("BOOK_A", state.lastSearchRequestForTest?.translationIds?.single())
            assertEquals("Genesis", state.lastSearchRequestForTest?.currentBookName)
        } finally {
            state.dispose()
            fixture.state.dispose()
            fixture.scope.cancel()
        }
    }

    /** A links-window result activates the links window; the source window's Find must stay in charge (#4136). */
    @Test fun linksWindowResultKeepsTheSourceSessionUntilAnotherWindowIsActivated() {
        val fixture = SearchFixture()
        val layout = MutableStateFlow(linksSnapshot("a"))
        val windows = object : WindowStateService { override val layout = layout }
        val platform = object : ReadingSearchPlatform by fixture.platform {
            override fun forWindow(windowId: String): ReadingSearchPlatform = object : ReadingSearchPlatform by fixture.platform {
                // WindowControl.showLink makes the links window active synchronously.
                override fun showInLinksWindow(list: net.bible.service.sword.BookAndKeyList) { layout.value = linksSnapshot("l") }
            }
        }
        val state = ReadingSearchState(fixture.settings, fixture.scope, platform, fixture.jobs,
            ReadingSearchServices(fixture.bible, FakeEpubSearch(), net.bible.sharedcore.search.SearchResultsCache(), fixture.index),
            onLeaveFullScreen = {}, windowState = windows)
        try {
            state.openSearch("light")
            val source = state.controller
            state.openSearchResultsInWindow()
            assertSame(source, state.controller, "the links window must not take over Find")
            assertEquals("light", state.searchBar.value?.query)
            layout.value = linksSnapshot("b")
            assertNotSame(source, state.controller, "activating another window moves Find to that window")
            assertNull(state.searchBar.value)
            layout.value = linksSnapshot("l")
            assertNotSame(source, state.controller, "the pin ends once another window was activated")
        } finally {
            state.dispose()
            fixture.state.dispose()
            fixture.scope.cancel()
        }
    }

    private fun linksSnapshot(active: String) = WindowLayoutState(
        windows = listOf("a", "b", "l").map { WindowSnapshot(it, WindowStateValue.VISIBLE, 1f, true, false, false, 0, it == "l") },
        activeWindowId = active, maximizedWindowId = null, reverseSplitMode = false, restoreButtonsVisible = true,
    )

    @Test fun findBelongsToWindowAndRestoresQueryResultsAndPosition() {
        val fixture = SearchFixture()
        val windows = Windows()
        val state = ReadingSearchState(fixture.settings, fixture.scope, fixture.platform, fixture.jobs,
            ReadingSearchServices(fixture.bible, FakeEpubSearch(), net.bible.sharedcore.search.SearchResultsCache(), fixture.index),
            onLeaveFullScreen = {}, windowState = windows)
        try {
            state.openSearch("light")
            val controllerA = state.controller
            val rowsA = state.searchResults
            val positionA = state.searchResultsListState.value
            state.searchResultsExpanded["Gen.1.1"] = true
            state.session.resultKeyboardSelection.value = 4
            state.controller.closeSheet()
            windows.activate("b")
            assertNull(state.searchBar.value, "another window must not inherit Find")
            assertFalse(state.searchSheetVisible.value)
            state.openSearch("hope")
            state.searchBarCallbacks.onQueryChange("hope refined")
            assertNotSame(controllerA, state.controller)
            assertEquals("light", controllerA.queries.query.value, "stable toolbar callbacks must route to the active owner")
            windows.activate("a")
            assertEquals("light", state.searchBar.value?.query)
            assertSame(controllerA, state.controller)
            assertSame(rowsA, state.searchResults)
            assertSame(positionA, state.searchResultsListState.value)
            assertEquals(true, state.searchResultsExpanded["Gen.1.1"])
            assertEquals(4, state.session.resultKeyboardSelection.value)
            assertFalse(state.searchSheetVisible.value, "returning must preserve whether results were dismissed")
            windows.activate("b")
            assertEquals("hope refined", state.searchBar.value?.query)
            state.leaveSearch()
            state.openSearch()
            assertEquals("", state.queries.query.value)
            state.searchBarCallbacks.onQueryChange("discard on close")
            val controllerB = state.controller
            windows.activate("a")
            windows.close("b")
            windows.layout.value = Windows.snapshot("b")
            assertNotSame(controllerB, state.controller)
            assertEquals("", state.queries.query.value, "closed windows discard their session even when their id returns")
        } finally {
            state.dispose()
            fixture.state.dispose()
            fixture.scope.cancel()
        }
    }
}
