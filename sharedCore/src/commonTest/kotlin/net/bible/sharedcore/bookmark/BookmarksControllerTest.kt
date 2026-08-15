package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.search.StyledText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun row(id: String) = BookmarkRow(
    id = id,
    title = "Row $id",
    dateText = "Mon, 2026-07-18 12:00",
    content = StyledText.plain(id),
    notes = null,
    labelColors = emptyList(),
    isSpeak = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksControllerTest {

    /** Records every loadRows() call + provides in-memory pref persistence for sort/showNotes. */
    private class FakeService(
        private val labels: List<BookmarkFilterLabel> = listOf(
            BookmarkFilterLabel(0, "All"),
            BookmarkFilterLabel(1, "Unlabeled"),
            BookmarkFilterLabel(2, "Work"),
        ),
        initialSort: BookmarkSortMode = BookmarkSortMode.BIBLE_ORDER,
        initialShowNotes: Boolean = true,
        private val rowsToReturn: (Int, BookmarkSortMode, String?, Boolean) -> List<BookmarkRow> =
            { _, _, _, _ -> listOf(row("A"), row("B")) },
    ) : BookmarksService {
        data class LoadCall(val filterIndex: Int, val sort: BookmarkSortMode, val search: String?, val showNotes: Boolean)

        var persistedSort: BookmarkSortMode = initialSort
        var persistedShowNotes: Boolean = initialShowNotes
        val loadCalls = mutableListOf<LoadCall>()

        /** Mutable so a test can change the label set between construction and a later refresh(). */
        var currentLabels: List<BookmarkFilterLabel> = labels

        override fun filterLabels(): List<BookmarkFilterLabel> = currentLabels
        override suspend fun loadRows(filterIndex: Int, sort: BookmarkSortMode, search: String?, showNotes: Boolean): List<BookmarkRow> {
            loadCalls.add(LoadCall(filterIndex, sort, search, showNotes))
            return rowsToReturn(filterIndex, sort, search, showNotes)
        }
        override fun loadSortMode(): BookmarkSortMode = persistedSort
        override fun saveSortMode(mode: BookmarkSortMode) { persistedSort = mode }
        override fun loadShowNotes(): Boolean = persistedShowNotes
        override fun saveShowNotes(v: Boolean) { persistedShowNotes = v }
    }

    private class Callbacks {
        var selected: Pair<String, Int>? = null
        var assigned: List<String>? = null
        var deleted: List<String>? = null
        var exportCalled = false
        var importCalled = false
        var manageLabelsCalled = false
    }

    private fun controller(
        service: FakeService = FakeService(),
        scope: CoroutineScope,
        initialFilterIndex: Int = 0,
        callbacks: Callbacks = Callbacks(),
    ): BookmarksController = BookmarksController(
        service = service,
        scope = scope,
        initialFilterIndex = initialFilterIndex,
        onSelectBookmark = { id, pos -> callbacks.selected = id to pos },
        onAssignLabels = { ids -> callbacks.assigned = ids },
        onDeleteSelected = { ids -> callbacks.deleted = ids },
        onExportCsv = { callbacks.exportCalled = true },
        onImportCsv = { callbacks.importCalled = true },
        onManageLabels = { callbacks.manageLabelsCalled = true },
    )

    @Test fun init_loads_rows_with_service_defaults() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService()
        val c = controller(service, backgroundScope)

        assertEquals(1, service.loadCalls.size)
        assertEquals(FakeService.LoadCall(0, BookmarkSortMode.BIBLE_ORDER, null, true), service.loadCalls.single())
        assertEquals(listOf("A", "B"), c.rows.value.map { it.id })
        assertFalse(c.loading.value)
        assertEquals(service.filterLabels(), c.filterLabels.value)
    }

    @Test fun setFilter_updates_index_and_reloads() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService()
        val c = controller(service, backgroundScope)

        c.setFilter(2)

        assertEquals(2, c.selectedFilterIndex.value)
        assertEquals(2, service.loadCalls.size)
        assertEquals(2, service.loadCalls.last().filterIndex)
    }

    @Test fun cycleSort_advances_persists_and_reloads() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService(initialSort = BookmarkSortMode.BIBLE_ORDER)
        val c = controller(service, backgroundScope)

        c.cycleSort()
        assertEquals(BookmarkSortMode.BIBLE_ORDER_DESC, c.sortMode.value)
        assertEquals(BookmarkSortMode.BIBLE_ORDER_DESC, service.persistedSort)
        assertEquals(BookmarkSortMode.BIBLE_ORDER_DESC, service.loadCalls.last().sort)

        c.cycleSort()
        assertEquals(BookmarkSortMode.CREATED_AT_DESC, c.sortMode.value)
        c.cycleSort()
        assertEquals(BookmarkSortMode.CREATED_AT, c.sortMode.value)
        c.cycleSort()
        assertEquals(BookmarkSortMode.BIBLE_ORDER, c.sortMode.value) // wraps
        assertEquals(5, service.loadCalls.size) // init + 4 cycles
    }

    @Test fun setSearch_reloads_with_text_blank_maps_to_null() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService()
        val c = controller(service, backgroundScope)

        c.setSearch("hello")
        assertEquals("hello", c.searchText.value)
        assertEquals("hello", service.loadCalls.last().search)

        c.setSearch("")
        assertEquals("", c.searchText.value) // state keeps the raw text
        assertNull(service.loadCalls.last().search) // but the seam call gets null
    }

    @Test fun toggleShowNotes_flips_persists_and_reloads() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService(initialShowNotes = true)
        val c = controller(service, backgroundScope)
        assertTrue(c.showNotes.value)

        c.toggleShowNotes()
        assertFalse(c.showNotes.value)
        assertFalse(service.persistedShowNotes)
        assertFalse(service.loadCalls.last().showNotes)

        c.toggleShowNotes()
        assertTrue(c.showNotes.value)
        assertTrue(service.persistedShowNotes)
    }

    @Test fun toggleShowNotes_off_clears_search() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService()
        val c = controller(service, backgroundScope)
        c.setSearch("needle")
        assertEquals("needle", c.searchText.value)

        c.toggleShowNotes() // true -> false
        assertEquals("", c.searchText.value)
        assertNull(service.loadCalls.last().search)
    }

    @Test fun toggleShowNotes_on_does_not_clear_search() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService(initialShowNotes = false)
        val c = controller(service, backgroundScope)
        c.setSearch("needle")

        c.toggleShowNotes() // false -> true
        assertEquals("needle", c.searchText.value) // not cleared when turning ON
    }

    @Test fun selection_enter_toggle_clear() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(scope = backgroundScope)

        c.enterSelection("A")
        assertEquals(setOf("A"), c.selection.value)

        c.toggleSelection("B")
        assertEquals(setOf("A", "B"), c.selection.value)

        c.toggleSelection("A")
        assertEquals(setOf("B"), c.selection.value)

        c.clearSelection()
        assertEquals(emptySet<String>(), c.selection.value)
    }

    @Test fun selectRow_routes_to_onSelectBookmark_when_selection_empty() = runTest(UnconfinedTestDispatcher()) {
        val callbacks = Callbacks()
        val c = controller(scope = backgroundScope, callbacks = callbacks)

        c.selectRow("A", 3)

        assertEquals("A" to 3, callbacks.selected)
        assertEquals(emptySet<String>(), c.selection.value)
    }

    @Test fun selectRow_toggles_selection_when_selection_not_empty() = runTest(UnconfinedTestDispatcher()) {
        val callbacks = Callbacks()
        val c = controller(scope = backgroundScope, callbacks = callbacks)
        c.enterSelection("A")

        c.selectRow("B", 1)

        assertNull(callbacks.selected) // NOT routed to onSelectBookmark
        assertEquals(setOf("A", "B"), c.selection.value)

        c.selectRow("A", 0) // toggling an already-selected row removes it
        assertEquals(setOf("B"), c.selection.value)
    }

    @Test fun assignSelected_and_deleteSelected_emit_id_list() = runTest(UnconfinedTestDispatcher()) {
        val callbacks = Callbacks()
        val c = controller(scope = backgroundScope, callbacks = callbacks)
        c.enterSelection("A")
        c.toggleSelection("B")

        c.assignSelected()
        assertEquals(listOf("A", "B"), callbacks.assigned)

        c.deleteSelected()
        assertEquals(listOf("A", "B"), callbacks.deleted)
    }

    @Test fun toolbar_actions_delegate_to_host() = runTest(UnconfinedTestDispatcher()) {
        val callbacks = Callbacks()
        val c = controller(scope = backgroundScope, callbacks = callbacks)

        c.exportCsv(); assertTrue(callbacks.exportCalled)
        c.importCsv(); assertTrue(callbacks.importCalled)
        c.manageLabels(); assertTrue(callbacks.manageLabelsCalled)
    }

    @Test fun refresh_reloads_and_clears_selection() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService()
        val c = controller(service, backgroundScope)
        c.enterSelection("A")
        val callsBefore = service.loadCalls.size

        c.refresh()

        assertEquals(emptySet<String>(), c.selection.value)
        assertEquals(callsBefore + 1, service.loadCalls.size)
    }

    @Test fun refresh_refetches_filterLabels_when_changed() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService()
        val c = controller(service, backgroundScope)
        c.enterSelection("A")
        val callsBefore = service.loadCalls.size
        assertEquals(service.currentLabels, c.filterLabels.value)

        val newLabels = listOf(
            BookmarkFilterLabel(0, "All"),
            BookmarkFilterLabel(1, "Unlabeled"),
            BookmarkFilterLabel(2, "Work"),
            BookmarkFilterLabel(3, "Home"),
        )
        service.currentLabels = newLabels

        c.refresh()

        assertEquals(newLabels, c.filterLabels.value)
        assertEquals(emptySet<String>(), c.selection.value)
        assertEquals(callsBefore + 1, service.loadCalls.size)
    }

    @Test fun initialFilterIndex_is_clamped_into_range() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeService() // 3 labels: indices 0..2
        val c = controller(service, backgroundScope, initialFilterIndex = 99)
        assertEquals(2, c.selectedFilterIndex.value)

        val c2 = controller(service, backgroundScope, initialFilterIndex = -5)
        assertEquals(0, c2.selectedFilterIndex.value)
    }

    @Test
    fun closeSearchLeavesSearchModeAndClearsTheText() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(scope = backgroundScope)
        c.setSearch("note")
        c.openSearch()
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.searchText.value)
    }

    @Test
    fun switchingShowNotesOffAlsoClosesSearchMode() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(scope = backgroundScope)          // showNotes starts on in this fixture; assert it, then toggle
        assertTrue(c.showNotes.value)
        c.openSearch()
        c.setSearch("note")
        c.toggleShowNotes()
        assertFalse(c.showNotes.value)
        assertFalse(c.searchModeActive.value, "the search icon is gone, so the bar must not stay in search mode")
        assertEquals("", c.searchText.value)
    }
}
