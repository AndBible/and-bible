package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManageLabelsControllerTest {

    private fun label(id: String, name: String, favourite: Boolean = false, isUnlabeled: Boolean = false) = LabelItem(
        id = id, name = name, color = 1, favourite = favourite, isUnlabeled = isUnlabeled,
        isSpecial = false, customIcon = null, hasOverride = false,
    )

    private class FakeService(
        private val labels: List<LabelItem>,
        private val recent: List<String> = emptyList(),
        private val overridden: Set<String> = emptySet(),
        private val unlabeled: LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null, false),
        private val contentSearch: suspend (String) -> List<ManageLabelsRow.SearchResult> = { emptyList() },
        var compact: Boolean = false,
    ) : ManageLabelsService {
        override fun assignableLabels() = labels
        override fun unlabeledLabel() = unlabeled
        override fun recentLabelIds() = recent
        override fun overriddenLabelIds() = overridden
        override fun randomColorArgb() = 0x11223344
        override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> = contentSearch(text)
        override fun compactLabelRows() = compact
        override fun setCompactLabelRows(value: Boolean) { compact = value }
    }

    private fun controller(
        mode: ManageLabelsMode,
        labels: List<LabelItem>,
        recent: List<String> = emptyList(),
        overridden: Set<String> = emptySet(),
        initialSelected: Set<String> = emptySet(),
        initialAutoAssign: Set<String> = emptySet(),
        initialAutoAssignPrimary: String? = null,
        initialBookmarkPrimary: String? = null,
        highlightLabelId: String? = null,
        unlabeled: LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null, false),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        contentSearch: suspend (String) -> List<ManageLabelsRow.SearchResult> = { emptyList() },
        compact: Boolean = false,
    ): ManageLabelsController = ManageLabelsController(
        mode = mode,
        service = FakeService(labels, recent, overridden, unlabeled, contentSearch, compact),
        scope = scope,
        initialSelected = initialSelected,
        initialAutoAssign = initialAutoAssign,
        initialAutoAssignPrimary = initialAutoAssignPrimary,
        initialBookmarkPrimary = initialBookmarkPrimary,
        highlightLabelId = highlightLabelId,
        onEditLabel = {},
        onSelectStudyPad = { _, _ -> },
        onSave = {},
        onReset = {},
    )

    /** Flattens rows to ids ("H_<CATEGORY>" for headers, label id for items) for order assertions. */
    private fun describe(rows: List<ManageLabelsRow>): List<String> = rows.map {
        when (it) {
            is ManageLabelsRow.Header -> "H_${it.category}"
            is ManageLabelsRow.Item -> it.label.id
            is ManageLabelsRow.SearchResult -> it.labelId
        }
    }

    private val A = label("A", "Apple")
    private val B = label("B", "Banana")
    private val C = label("C", "Cherry")
    private val D = label("D", "Date")
    private val E = label("E", "Elderberry")
    private val F = label("F", "Fig")

    @Test fun categorization_and_sort_order_ASSIGN() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(F, E, D, C, B, A), // scrambled input order
            recent = listOf("B", "D"),
            initialSelected = setOf("A", "C"),
        )
        assertEquals(
            listOf("H_ACTIVE", "A", "C", "H_RECENT", "B", "D", "H_OTHER", "E", "F"),
            describe(c.rows.value),
        )
    }

    @Test fun hideCategories_STUDYPAD_no_headers_alpha_sorted() {
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(C, A, B))
        assertEquals(listOf("A", "B", "C"), describe(c.rows.value))
    }

    @Test fun showUnassigned_WORKSPACE_inserts_unlabeled() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A))
        assertTrue(describe(c.rows.value).contains("UNL"))
    }

    @Test fun showUnassigned_ASSIGN_does_not_insert_unlabeled() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = listOf(A))
        assertFalse(describe(c.rows.value).contains("UNL"))
    }

    @Test fun showUnassigned_suppressed_once_already_in_changed() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A))
        assertEquals(1, describe(c.rows.value).count { it == "UNL" })
        // host applies a round-trip edit of the special Unlabeled label -> it lands in `labels`
        // AND `changed`, so the separate special-insertion path must not duplicate it.
        c.applyLabelChanged(label("UNL", "Unlabeled", isUnlabeled = true), false, false, false, false)
        assertEquals(1, describe(c.rows.value).count { it == "UNL" })
    }

    @Test fun toggleChecked_sets_and_reassigns_primary() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = listOf(A, B))

        c.toggleChecked("A")
        var aRow = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        assertTrue(aRow.checked); assertTrue(aRow.isPrimary)

        c.toggleChecked("B")
        val bRow = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "B" }
        assertTrue(bRow.checked); assertFalse(bRow.isPrimary) // primary stays A

        c.toggleChecked("A") // uncheck A -> primary reassigned to remaining B
        aRow = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        val bRow2 = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "B" }
        assertFalse(aRow.checked)
        assertTrue(bRow2.isPrimary)

        c.toggleChecked("B") // uncheck last selection -> primary cleared
        assertEquals(emptySet<String>(), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())
    }

    @Test fun toggleAutoAssign_WORKSPACE_mirrors_primary_logic() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A, B))

        c.toggleAutoAssign("A")
        assertEquals(setOf("A"), c.resultAutoAssign())
        assertEquals("A", c.resultAutoAssignPrimary())

        c.toggleAutoAssign("B")
        assertEquals(setOf("A", "B"), c.resultAutoAssign())
        assertEquals("A", c.resultAutoAssignPrimary()) // unchanged

        c.toggleAutoAssign("A") // remove primary -> reassigned to remaining B
        assertEquals(setOf("B"), c.resultAutoAssign())
        assertEquals("B", c.resultAutoAssignPrimary())
    }

    @Test fun toggleFavourite_flips_and_marks_changed() {
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(A))
        assertFalse(c.resultChanged().contains("A"))

        c.toggleFavourite("A")
        assertTrue(c.resultChanged().contains("A"))
        val row = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        assertTrue(row.label.favourite)

        c.toggleFavourite("A")
        val row2 = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        assertFalse(row2.label.favourite)
    }

    @Test fun toggleFavourite_currentLabelItems_reflects_the_flip() {
        // Regression for the favourite-persistence finding: the host reads currentLabelItems() at
        // save time to source the favourite value onto its own authoritative Label map, since
        // toggleFavourite only flips this controller's own copy.
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(A, B))
        assertFalse(c.currentLabelItems().first { it.id == "A" }.favourite)
        assertFalse(c.currentLabelItems().first { it.id == "B" }.favourite)

        c.toggleFavourite("A")
        assertTrue(c.currentLabelItems().first { it.id == "A" }.favourite)
        assertFalse(c.currentLabelItems().first { it.id == "B" }.favourite) // untouched

        c.toggleFavourite("A")
        assertFalse(c.currentLabelItems().first { it.id == "A" }.favourite)
    }

    @Test fun applyLabelDeleted_reassigns_primary_and_cleans_changed() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(A, B),
            initialSelected = setOf("A", "B"),
            initialBookmarkPrimary = "A",
        )
        c.toggleFavourite("A") // marks "A" as changed
        assertTrue(c.resultChanged().contains("A"))

        c.applyLabelDeleted("A", orphaned = false)
        assertEquals(setOf("B"), c.resultSelected())
        assertEquals("B", c.resultBookmarkPrimary()) // reassigned to remaining member
        assertFalse(c.resultChanged().contains("A")) // dropped from changed
        assertTrue(c.resultDeleted().contains("A"))

        c.applyLabelDeleted("B", orphaned = true)
        assertEquals(emptySet<String>(), c.resultSelected())
        assertNull(c.resultBookmarkPrimary()) // set now empty -> reassigned to null
        assertTrue(c.resultDeletedWithOrphaned().contains("B"))
    }

    @Test fun applyLabelChanged_bookmarkPrimaryFlag_false_reassigns_primary_away() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(A, B),
            initialSelected = setOf("B", "A"), // "B" precedes "A" -> firstOrNull() picks it on reassignment
            initialBookmarkPrimary = "A",
        )
        // "A" comes back from the edit round-trip still selected but with isThisBookmarkPrimary == false:
        // ensureNotBookmarkPrimaryLabel must still fire (primary == this label's id) and reassign away,
        // even though the label itself remains in the selected set.
        c.applyLabelChanged(A.copy(name = "Apple2"), selectedFlag = true, autoAssignFlag = false, bookmarkPrimaryFlag = false, autoAssignPrimaryFlag = false)
        assertEquals(setOf("B", "A"), c.resultSelected())
        assertEquals("B", c.resultBookmarkPrimary())
    }

    @Test fun applyLabelChanged_both_primaries_independent_of_mode() {
        // Classic ManageLabels.editLabel (ManageLabels.kt:614-628) applies isAutoAssignPrimary and
        // isThisBookmarkPrimary UNCONDITIONALLY -- not mode-gated -- so both must propagate even when
        // editing from an ASSIGN-mode session, whose own contextPrimary() mapping only ever reads/writes
        // bookmarkPrimary. A prior bug drove only ONE mode-mapped primary and silently dropped the other.
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(A, B),
            initialSelected = setOf("B", "A"),
            initialAutoAssign = setOf("B", "A"),
            initialBookmarkPrimary = "A",
            initialAutoAssignPrimary = "A",
        )
        // Both primary flags true on "A" (already primary in both) -> both stay "A".
        c.applyLabelChanged(A.copy(name = "Apple2"), selectedFlag = true, autoAssignFlag = true, bookmarkPrimaryFlag = true, autoAssignPrimaryFlag = true)
        assertEquals("A", c.resultBookmarkPrimary())
        assertEquals("A", c.resultAutoAssignPrimary())

        // Both primary flags false on the same item "A" -> ensureNot*Primary fallbacks fire
        // independently for bookmarkPrimary and autoAssignPrimary, both reassigning to "B".
        c.applyLabelChanged(A.copy(name = "Apple3"), selectedFlag = true, autoAssignFlag = true, bookmarkPrimaryFlag = false, autoAssignPrimaryFlag = false)
        assertEquals("B", c.resultBookmarkPrimary())
        assertEquals("B", c.resultAutoAssignPrimary())
    }

    @Test fun toggleChecked_HIDELABELS_no_primary_but_selection_toggles() {
        val c = controller(mode = ManageLabelsMode.HIDELABELS, labels = listOf(A, B))
        assertTrue(ManageLabelsMode.HIDELABELS.showCheckboxes)
        assertFalse(ManageLabelsMode.HIDELABELS.primaryShown)

        c.toggleChecked("A")
        assertEquals(setOf("A"), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())
        assertNull(c.resultAutoAssignPrimary())

        c.toggleChecked("B")
        assertEquals(setOf("A", "B"), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())

        c.toggleChecked("A")
        assertEquals(setOf("B"), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())
    }

    @Test fun name_filter_NAME_START_vs_NAME_CONTAINS() {
        val antelope = label("ANT", "Antelope")
        val banana = label("BAN", "Banana")
        val cantaloupe = label("CAN", "Cantaloupe")
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(antelope, banana, cantaloupe))

        c.setSearch("an")
        assertEquals(listOf("ANT"), describe(c.rows.value)) // NAME_START (default): only "Antelope" starts with "an"

        c.setSearchMode(SearchMode.NAME_CONTAINS)
        assertEquals(listOf("ANT", "BAN", "CAN"), describe(c.rows.value)) // NAME_CONTAINS: all three contain "an"
    }

    @Test fun name_filter_already_selected_label_bypasses_a_non_matching_search() {
        // Classic ManageLabels.kt:847-850 labelMatches: an already-selected label is always shown,
        // even if its name doesn't match the current search text. STUDYPAD hides category headers
        // (hideCategories), so `describe` reflects only the filtered item set.
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(A, B), initialSelected = setOf("B"))

        c.setSearch("zzz") // matches neither "Apple" nor "Banana"
        assertEquals(listOf("B"), describe(c.rows.value)) // "B" survives via the selected bypass; "A" is filtered out
    }

    @Test fun setSearchMode_defaults_to_NAME_START() {
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(A, B))
        assertEquals(SearchMode.NAME_START, c.searchMode.value)
    }

    @Test fun name_filter_bypass_is_a_no_op_in_WORKSPACE_mode() {
        // Classic ManageLabels.kt:847-850 labelMatches uses the RAW data.selectedLabels field, never
        // the mode-aware getter -- and WORKSPACE-mode ManageLabelsData never populates selectedLabels
        // (only autoAssignLabels). So a label that's auto-assigned (but not in the raw `selected` set)
        // must NOT bypass the name filter in WORKSPACE mode, even though WORKSPACE's contextSelected()
        // resolves to `autoAssign`. This locks in classic parity against the contextSelected()-bypass bug.
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A, B), initialAutoAssign = setOf("B"))

        c.setSearch("zzz") // matches neither "Apple" nor "Banana"
        assertFalse(describe(c.rows.value).contains("B")) // NOT bypassed: autoAssign membership alone doesn't count
    }

    @Test fun name_filter_already_selected_label_bypasses_a_non_matching_search_ASSIGN() {
        // Contrast with the WORKSPACE case above: in ASSIGN mode contextSelected() == selected, so a
        // raw-selected label with a non-matching name IS bypassed (the classic behaviour this whole
        // bypass exists for).
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = listOf(A, B), initialSelected = setOf("B"))

        c.setSearch("zzz") // matches neither "Apple" nor "Banana"
        assertTrue(describe(c.rows.value).contains("B")) // "B" survives via the raw selected bypass
    }

    @Test fun name_filter_CONTENT_falls_back_to_NAME_CONTAINS() {
        // CONTENT's name-branch is unused once content search (Task 2) is active; as a name-filter
        // fallback (e.g. before content search results are available) it behaves like NAME_CONTAINS.
        val antelope = label("ANT", "Antelope")
        val banana = label("BAN", "Banana")
        val cantaloupe = label("CAN", "Cantaloupe")
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(antelope, banana, cantaloupe))

        c.setSearchMode(SearchMode.CONTENT)
        c.setSearch("an")
        assertEquals(listOf("ANT", "BAN", "CAN"), describe(c.rows.value))
    }

    private fun searchResult(labelId: String, name: String = labelId) = ManageLabelsRow.SearchResult(
        labelId = labelId, name = name, color = 1, matchCount = 1, snippet = "snippet",
        matchStart = 0, matchEnd = 1, firstMatchEntryId = "entry-$labelId",
    )

    @Test fun content_search_debounces_and_yields_fake_results() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val fakeResults = listOf(searchResult("A"), searchResult("B"))
        val c = controller(
            mode = ManageLabelsMode.STUDYPAD,
            labels = listOf(A, B),
            scope = scope,
            contentSearch = { fakeResults },
        )
        c.setSearchMode(SearchMode.CONTENT)
        c.setSearch("hello")

        // Not yet debounced: still the categorized (name) list, not search results.
        assertFalse(c.rows.value.any { it is ManageLabelsRow.SearchResult })

        scope.advanceTimeBy(300)
        scope.advanceUntilIdle()

        assertEquals(listOf("A", "B"), describe(c.rows.value))
        assertTrue(c.rows.value.all { it is ManageLabelsRow.SearchResult })
    }

    @Test fun content_search_short_text_falls_back_to_categorized_list() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var calls = 0
        val c = controller(
            mode = ManageLabelsMode.STUDYPAD,
            labels = listOf(A, B),
            scope = scope,
            contentSearch = { calls++; listOf(searchResult("A")) },
        )
        c.setSearchMode(SearchMode.CONTENT)
        c.setSearch("ab") // 2 chars: below the 3-char threshold

        scope.advanceTimeBy(300)
        scope.advanceUntilIdle()

        assertEquals(0, calls) // content search never invoked
        // categorized (name-filtered) list, not SearchResults; neither "Apple" nor "Banana"
        // contains "ab" so the fallback name filter (CONTENT behaves like NAME_CONTAINS) yields none.
        assertEquals(emptyList<String>(), describe(c.rows.value))
    }

    @Test fun content_search_debounce_cancels_stale_query_only_latest_lands() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val c = controller(
            mode = ManageLabelsMode.STUDYPAD,
            labels = listOf(A, B),
            scope = scope,
            contentSearch = { text -> listOf(searchResult(text)) },
        )
        c.setSearchMode(SearchMode.CONTENT)
        c.setSearch("first")
        scope.advanceTimeBy(100) // still within debounce window: not yet fired
        c.setSearch("second")
        scope.advanceTimeBy(300)
        scope.advanceUntilIdle()

        // Only the latest ("second") query's results should land, not "first"'s.
        assertEquals(listOf("second"), describe(c.rows.value))
    }

    @Test fun content_search_switching_away_from_CONTENT_restores_categorized_list() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val c = controller(
            mode = ManageLabelsMode.STUDYPAD,
            labels = listOf(A, B),
            scope = scope,
            contentSearch = { listOf(searchResult("A"), searchResult("B")) },
        )
        c.setSearchMode(SearchMode.CONTENT)
        c.setSearch("ban") // >= 3 chars; contained in "Banana" only (name-filter fallback semantics)
        scope.advanceTimeBy(300)
        scope.advanceUntilIdle()
        assertTrue(c.rows.value.all { it is ManageLabelsRow.SearchResult })

        c.setSearchMode(SearchMode.NAME_CONTAINS) // switching away restores the categorized list
        assertEquals(listOf("B"), describe(c.rows.value)) // only "Banana" contains "ban"
        assertTrue(c.rows.value.all { it is ManageLabelsRow.Item })
    }

    @Test fun content_search_closeSearch_during_live_content_search_restores_categorized_list() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val c = controller(
            mode = ManageLabelsMode.STUDYPAD,
            labels = listOf(A, B),
            scope = scope,
            contentSearch = { listOf(searchResult("A"), searchResult("B")) },
        )
        c.openSearch()
        c.setSearchMode(SearchMode.CONTENT)
        c.setSearch("ban") // >= 3 chars; contained in "Banana" only (name-filter fallback semantics)
        scope.advanceTimeBy(300)
        scope.advanceUntilIdle()
        assertTrue(c.rows.value.all { it is ManageLabelsRow.SearchResult })

        // closeSearch (hardware back / the bar's back arrow), not setSearchMode: it must clear the
        // query AND drop the stale result rows, not leave the last content-search hits on screen
        // under a now-empty query. With no query, both labels pass the (blank-query-matches-all)
        // name filter, so the categorized list is the full A/B set, not just "B" the way the
        // sibling test above sees it (that one leaves "ban" in place).
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.searchText.value)
        assertEquals(listOf("A", "B"), describe(c.rows.value))
        assertTrue(c.rows.value.none { it is ManageLabelsRow.SearchResult })
    }

    @Test fun selectStudyPad_two_arg_overload_forwards_labelId_and_firstMatchEntryId() {
        var received: Pair<String, String?>? = null
        val c = ManageLabelsController(
            mode = ManageLabelsMode.STUDYPAD,
            service = FakeService(listOf(A)),
            scope = CoroutineScope(Dispatchers.Unconfined),
            initialSelected = emptySet(),
            initialAutoAssign = emptySet(),
            initialAutoAssignPrimary = null,
            initialBookmarkPrimary = null,
            highlightLabelId = null,
            onEditLabel = {},
            onSelectStudyPad = { id, entryId -> received = id to entryId },
            onSave = {},
            onReset = {},
        )
        c.selectStudyPad("A", "entry-42")
        assertEquals("A" to "entry-42", received)
    }

    @Test
    fun `search mode starts closed`() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = emptyList())
        assertFalse(c.searchModeActive.value)
    }

    @Test
    fun `opening search mode does not touch the query`() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = emptyList())
        c.setSearch("gen")
        c.openSearch()
        assertTrue(c.searchModeActive.value)
        assertEquals("gen", c.searchText.value)
    }

    @Test
    fun `closing search mode clears the query`() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = emptyList())
        c.openSearch()
        c.setSearch("gen")
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.searchText.value)
    }

    @Test
    fun `closing search mode is safe when it was never opened`() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = emptyList())
        c.setSearch("gen")
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.searchText.value)
    }

    @Test fun compact_row_mode_is_seeded_from_the_service_and_written_back() {
        val service = FakeService(labels = listOf(label("L1", "Study")), compact = true)
        val c = ManageLabelsController(
            mode = ManageLabelsMode.ASSIGN,
            service = service,
            scope = CoroutineScope(Dispatchers.Unconfined),
            initialSelected = emptySet(),
            initialAutoAssign = emptySet(),
            initialAutoAssignPrimary = null,
            initialBookmarkPrimary = null,
            highlightLabelId = null,
            onEditLabel = {},
            onSelectStudyPad = { _, _ -> },
            onSave = {},
            onReset = {},
        )

        // Seeded, not defaulted: the setting is global and survives across openings of the screen.
        assertTrue(c.compact.value)

        c.toggleCompact()
        assertFalse(c.compact.value)
        // Written through immediately -- there is no Save button for a view preference.
        assertFalse(service.compact)
    }
}
