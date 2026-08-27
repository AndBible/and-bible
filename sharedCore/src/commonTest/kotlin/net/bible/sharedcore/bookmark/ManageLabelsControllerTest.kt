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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManageLabelsControllerTest {

    private fun label(
        id: String, name: String, favourite: Boolean = false, isUnlabeled: Boolean = false,
        overrideStyle: BookmarkDisplayStyle? = null,
    ) = LabelItem(
        id = id, name = name, color = 1, favourite = favourite, isUnlabeled = isUnlabeled,
        isSpecial = false, customIcon = null, overrideStyle = overrideStyle,
    )

    private class FakeService(
        private val labels: List<LabelItem>,
        private val recent: List<String> = emptyList(),
        private val overridden: Map<String, BookmarkDisplayStyle> = emptyMap(),
        private val unlabeled: LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null),
        private val contentSearch: suspend (String) -> List<ManageLabelsRow.SearchResult> = { emptyList() },
        var styleTags: Boolean = true,
    ) : ManageLabelsService {
        override fun assignableLabels() = labels
        override fun unlabeledLabel() = unlabeled
        override fun recentLabelIds() = recent
        override fun overriddenLabelStyles() = overridden
        override fun randomColorArgb() = 0x11223344
        override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> = contentSearch(text)
        override fun styleTagsVisible() = styleTags
        override fun setStyleTagsVisible(visible: Boolean) { styleTags = visible }
    }

    private fun controller(
        mode: ManageLabelsMode,
        labels: List<LabelItem> = emptyList(),
        recent: List<String> = emptyList(),
        overridden: Map<String, BookmarkDisplayStyle> = emptyMap(),
        initialSelected: Set<String> = emptySet(),
        initialAutoAssign: Set<String> = emptySet(),
        initialAutoAssignPrimary: String? = null,
        initialBookmarkPrimary: String? = null,
        highlightLabelId: String? = null,
        unlabeled: LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        contentSearch: suspend (String) -> List<ManageLabelsRow.SearchResult> = { emptyList() },
        // Override to hand in a FakeService instance the test still holds a reference to (e.g. to
        // assert the write-through of a persisted preference); defaults to a fresh one built from
        // the params above, exactly as before this parameter existed.
        service: ManageLabelsService = FakeService(labels, recent, overridden, unlabeled, contentSearch),
    ): ManageLabelsController = ManageLabelsController(
        mode = mode,
        service = service,
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

    @Test
    fun overrideStyle_is_relinked_onto_every_rebuild() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(label("L1", "Study"), label("L2", "Notes")),
            overridden = mapOf("L2" to BookmarkDisplayStyle.MARKER),
        )

        val items = c.rows.value.filterIsInstance<ManageLabelsRow.Item>()
        assertNull(items.first { it.label.id == "L1" }.label.overrideStyle)
        assertEquals(BookmarkDisplayStyle.MARKER, items.first { it.label.id == "L2" }.label.overrideStyle)

        // The STICKY path (any toggle) must relink just as faithfully as the initial reorder=true
        // rebuild above -- otherwise a future edit that moves the relink after the sticky/fresh
        // branch would silently blank the ⚙ tag on every toggle, with no crash and no failing test.
        c.toggleChecked("L2")
        val afterToggle = c.rows.value.filterIsInstance<ManageLabelsRow.Item>()
        assertEquals(BookmarkDisplayStyle.MARKER, afterToggle.first { it.label.id == "L2" }.label.overrideStyle)
    }

    /** The Unlabeled pseudo-row is added raw, alongside every real label's relink -- classic's
     *  adapter shows the ⚙ mark for any overridden id, Unlabeled included
     *  (ManageLabelItemAdapter.kt:236), so this controller must not special-case it out. */
    @Test
    fun overrideStyle_reaches_the_unlabeled_row_too() {
        val c = controller(
            mode = ManageLabelsMode.WORKSPACE,
            labels = listOf(label("L1", "Study")),
            overridden = mapOf("UNL" to BookmarkDisplayStyle.HIGHLIGHT),
        )

        val items = c.rows.value.filterIsInstance<ManageLabelsRow.Item>()
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, items.first { it.label.id == "UNL" }.label.overrideStyle)
    }

    private fun threeLabels() = listOf(label("L1", "Study"), label("L2", "Notes"), label("L3", "Prayer"))

    /** The bug this round fixes: in WORKSPACE mode contextSelected() IS the auto-assign set, so a
     *  re-sorting rebuild re-buckets the just-toggled row into ACTIVE and re-alphabetises it — the
     *  row jumps out from under the finger and the ACTIVE header appears above it. Classic ends the
     *  same handler with updateLabelList(rePopulate = false, reOrder = false)
     *  (ManageLabels.kt:865), which skips its sortWith entirely. */
    @Test
    fun toggling_auto_assign_does_not_move_the_row_or_add_a_header() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = threeLabels())
        val before = describe(c.rows.value)

        c.toggleAutoAssign("L3")

        assertEquals(before, describe(c.rows.value))
        assertFalse(describe(c.rows.value).contains("H_ACTIVE"), "no ACTIVE header may appear")
        val row = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "L3" }
        assertTrue(row.isAutoAssign, "the toggle's own state must still change")
    }

    @Test
    fun the_other_three_toggles_do_not_move_rows_either() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = threeLabels())
        val before = describe(c.rows.value)
        fun l3() = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "L3" }

        // Each assertion below pins that the toggle's OWN field actually moved, not merely that the
        // list order didn't -- otherwise these three checks would pass just as well against a toggle
        // that silently did nothing.
        c.toggleChecked("L3")
        assertEquals(before, describe(c.rows.value))
        assertTrue(l3().checked, "toggleChecked must actually flip checked")

        c.setPrimary("L3")
        assertEquals(before, describe(c.rows.value))
        assertTrue(l3().isPrimary, "setPrimary must actually flip isPrimary")

        c.toggleFavourite("L3")
        assertEquals(before, describe(c.rows.value))
        assertTrue(l3().label.favourite, "toggleFavourite must actually flip favourite")
    }

    @Test fun toggleStyleTags_flips_and_writes_through() {
        val service = FakeService(labels = threeLabels())
        val c = controller(mode = ManageLabelsMode.WORKSPACE, service = service)
        assertTrue(c.styleTagsVisible.value)
        c.toggleStyleTags()
        assertFalse(c.styleTagsVisible.value)
        assertFalse(service.styleTags)
        c.toggleStyleTags()
        assertTrue(c.styleTagsVisible.value)
        assertTrue(service.styleTags)
    }

    @Test fun toggleStyleTags_seeds_from_the_service() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, service = FakeService(labels = threeLabels(), styleTags = false))
        assertFalse(c.styleTagsVisible.value)
    }

    @Test fun toggleStyleTags_does_not_rebuild_the_rows() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, service = FakeService(labels = threeLabels()))
        val before = c.rows.value
        c.toggleStyleTags()
        assertSame(before, c.rows.value)
    }

    /** Of the three host apply hooks, only a RENAME actually exercises `reorder = true`
     *  (ManageLabelsController.kt:279): create/delete change the visible id set, so
     *  [stickyOrder]'s own "set changed -> full sort" fallback would re-sort them anyway even with
     *  reorder=false. A rename changes no id, so nothing else forces the re-sort -- if
     *  applyLabelChanged ever stopped rebuilding with reorder=true, a rename that moves a label's
     *  alphabetical position would silently stay parked in its old slot. */
    @Test
    fun a_rename_through_applyLabelChanged_re_alphabetises() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = threeLabels())
        // threeLabels(): L1 "Study", L2 "Notes", L3 "Prayer", none selected/recent -> all three fall
        // into OTHER, alpha order Notes, Prayer, Study. ASSIGN doesn't hide categories, so H_RECENT
        // and H_OTHER are present even empty/single-bucket (see categorization_and_sort_order_ASSIGN).
        assertEquals(listOf("H_RECENT", "H_OTHER", "L2", "L3", "L1"), describe(c.rows.value))

        c.applyLabelChanged(
            item = label("L1", "Aardvark"),
            selectedFlag = false,
            autoAssignFlag = false,
            bookmarkPrimaryFlag = false,
            autoAssignPrimaryFlag = false,
        )

        assertEquals(listOf("H_RECENT", "H_OTHER", "L1", "L2", "L3"), describe(c.rows.value))
    }

    @Test
    fun un_toggling_the_last_auto_assign_does_not_remove_the_header_either() {
        val c = controller(
            mode = ManageLabelsMode.WORKSPACE,
            labels = threeLabels(),
            initialAutoAssign = setOf("L3"),
        )
        val before = describe(c.rows.value)
        assertTrue(before.contains("H_ACTIVE"), "precondition: the ACTIVE header is present")

        c.toggleAutoAssign("L3")

        assertEquals(before, describe(c.rows.value))
    }

    @Test
    fun reOrder_regroups_what_the_toggles_left_in_place() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = threeLabels())
        c.toggleAutoAssign("L3")
        val stuck = describe(c.rows.value)

        c.reOrder()

        val regrouped = describe(c.rows.value)
        assertTrue(regrouped.contains("H_ACTIVE"), "regrouping is what adds the header")
        assertEquals("H_ACTIVE", regrouped.first())
        assertEquals("L3", regrouped[1])
        assertTrue(stuck != regrouped)
    }

    @Test
    fun a_search_dispatch_reorders() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = threeLabels())
        c.toggleChecked("L3")
        val stuck = describe(c.rows.value)

        c.setSearch("")   // the same (empty) query, but a search dispatch all the same

        assertTrue(stuck != describe(c.rows.value))
        assertTrue(describe(c.rows.value).contains("H_ACTIVE"))
    }

    /** The defensive fallback. `nameMatches` bypasses the filter for anything in `selected`
     *  (classic ManageLabels.kt:847-850), so checking a label the query hides makes the visible set
     *  GROW during a sticky rebuild. The old sequence has no place for it, so the rebuild must fall
     *  back to a full sort rather than drop it or emit it twice. */
    @Test
    fun a_sticky_rebuild_whose_visible_set_grew_falls_back_to_a_full_sort() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = threeLabels())
        c.setSearch("Stu")
        assertEquals(listOf("L1"), describe(c.rows.value).filterNot { it.startsWith("H_") })

        c.toggleChecked("L2")

        val ids = describe(c.rows.value).filterNot { it.startsWith("H_") }
        assertEquals(setOf("L1", "L2"), ids.toSet())
        assertEquals(ids.size, ids.toSet().size, "no row may be emitted twice")
    }

}
