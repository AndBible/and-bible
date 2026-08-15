/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSelectionControllerTest {
    private val en = LangOption("en", "English", "en")
    private val fi = LangOption("fi", "Finnish", "fi")
    private fun row(
        id: String, cat: DocCategory, lang: LangOption = en,
        status: DocInstallStatus = DocInstallStatus.NOT_INSTALLED, recommended: Boolean = false, abbr: String = id,
    ) = DocRow(id, "osis-$id", abbr, "name $id", lang, "repo", cat, status, 0, recommended, false, false, false, false, null)

    private fun controller() = DocumentSelectionController(
        langComparator = compareBy { it.displayName },
        onSelect = {}, onDelete = {}, onDeleteIndex = {}, onAbout = {}, onUnlock = {}, onStickyLanguage = {},
    )

    @Test fun starts_loading_empty() {
        val c = controller()
        assertTrue(c.loading.value)
        assertTrue(c.displayed.value.isEmpty())
    }

    @Test fun setDocuments_clears_loading_and_builds_language_list_deduped_sorted() {
        val c = controller()
        // two rows that group to the same key must appear once; languages sorted by comparator
        val enAlt = LangOption("eng", "English", "en")
        c.setDocuments(listOf(row("a", DocCategory.BIBLE, en), row("b", DocCategory.BIBLE, fi), row("c", DocCategory.BIBLE, enAlt)), searchIds = null)
        assertFalse(c.loading.value)
        assertEquals(listOf("English", "Finnish"), c.languages.value.map { it.displayName })
    }

    @Test fun type_filter_all_excludes_addons() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("x", DocCategory.AND_BIBLE)), null)
        c.setTypeFilter(DocTypeFilter.ALL)
        assertEquals(listOf("a"), c.displayed.value.map { it.docId })
    }

    @Test fun type_filter_bible_only() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.COMMENTARY)), null)
        c.setTypeFilter(DocTypeFilter.BIBLE)
        assertEquals(listOf("a"), c.displayed.value.map { it.docId })
    }

    @Test fun language_filter_matches_grouping_key_and_always_keeps_andbible() {
        val c = controller()
        val rows = listOf(row("a", DocCategory.BIBLE, en), row("b", DocCategory.BIBLE, fi), row("x", DocCategory.AND_BIBLE, fi))
        c.setDocuments(rows, null)
        // Case 1: type=ALL removes AND_BIBLE entirely, so the "always keep AND_BIBLE" language
        // clause never gets a chance to save row "x". With language=en, "b" (fi) is also filtered out.
        c.setTypeFilter(DocTypeFilter.ALL)
        c.setLanguage(en)
        assertEquals(listOf("a"), c.displayed.value.map { it.docId }) // fi filtered out, addon removed by ALL filter

        // Case 2: exercise the language rule's "always keep AND_BIBLE" clause with a type filter that
        // INCLUDES addons (ADDON). Even with a non-matching language (en) the fi AND_BIBLE row survives.
        c.setTypeFilter(DocTypeFilter.ADDON)
        c.setLanguage(en)
        assertEquals(listOf("x"), c.displayed.value.map { it.docId })
    }

    @Test fun search_below_3_chars_is_ignored() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE)), searchIds = null)
        c.setQuery("ab") // < 3 chars -> host should not have searched; controller keeps all
        assertEquals(2, c.displayed.value.size)
    }

    @Test fun search_results_intersect_by_osisId() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE)), searchIds = setOf("osis-a"))
        assertEquals(listOf("a"), c.displayed.value.map { it.docId })
    }

    @Test fun sort_puts_being_installed_first_then_not_installed_then_category_then_abbr() {
        val c = controller()
        val rows = listOf(
            row("z", DocCategory.COMMENTARY, status = DocInstallStatus.INSTALLED, abbr = "z"),
            row("a", DocCategory.BIBLE, status = DocInstallStatus.NOT_INSTALLED, abbr = "a"),
            row("d", DocCategory.BIBLE, status = DocInstallStatus.BEING_INSTALLED, abbr = "d"),
        )
        c.setDocuments(rows, null)
        c.setTypeFilter(DocTypeFilter.ALL)
        // Classic-faithful order (DocumentSelectionBase sort key 2 = `getDocumentByInitials(...) == null`,
        // false < true): BEING_INSTALLED first (d), then among the "else" tier INSTALLED (z, false)
        // sorts before NOT_INSTALLED (a, true). So the expected order is d, z, a.
        assertEquals(listOf("d", "z", "a"), c.displayed.value.map { it.docId })
    }

    @Test fun selection_toggle_and_autoexit_on_refilter() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE)), null)
        c.enterSelection(); c.toggle("a")
        assertTrue(c.selectionMode.value); assertEquals(setOf("a"), c.selectedIds.value)
        c.setTypeFilter(DocTypeFilter.COMMENTARY) // list changes -> exit selection
        assertFalse(c.selectionMode.value); assertTrue(c.selectedIds.value.isEmpty())
    }

    @Test fun setLanguage_reports_sticky() {
        var sticky: LangOption? = null
        val c = DocumentSelectionController(compareBy { it.displayName }, {}, {}, {}, {}, {}, { sticky = it })
        c.setDocuments(listOf(row("a", DocCategory.BIBLE, fi)), null)
        c.setLanguage(fi)
        assertEquals(fi, sticky)
    }

    @Test fun select_delete_about_forward_to_seams() {
        var selected: String? = null; var deleted: Set<String>? = null
        val c = DocumentSelectionController(compareBy { it.displayName }, { selected = it }, { deleted = it }, {}, {}, {}, {})
        c.setDocuments(listOf(row("a", DocCategory.BIBLE)), null)
        c.select("a"); assertEquals("a", selected)
        c.enterSelection(); c.toggle("a"); c.delete(); assertEquals(setOf("a"), deleted)
    }

    @Test fun error_show_dismiss() {
        val c = controller()
        c.showError(); assertEquals(ChooserError.FAILED, c.error.value)
        c.dismissError(); assertNull(c.error.value)
    }

    /**
     * Updated 2026-08-14 (Round 6 download-row fix): this test used to pin the exact defect being
     * fixed here — a progress tick re-sorted the displayed list and floated BEING_INSTALLED to the
     * top, moving the row out from under the user. updateDownloadStatus now updates the row IN
     * PLACE and leaves sorting to the four compositional triggers (setDocuments/setLanguage/
     * setTypeFilter/setSearchResults). See download_progress_keeps_the_row_at_its_index and
     * download_progress_does_not_clear_an_active_selection below for the dedicated coverage.
     */
    @Test fun updateDownloadStatus_does_not_float_and_preserves_selection() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE, abbr = "a"), row("b", DocCategory.BIBLE, abbr = "b")), null)
        c.setTypeFilter(DocTypeFilter.ALL)
        assertEquals(listOf("a", "b"), c.displayed.value.map { it.docId })
        // Enter selection and select "a" — a progress tick must NOT wipe this.
        c.enterSelection(); c.toggle("a")
        assertTrue(c.selectionMode.value); assertEquals(setOf("a"), c.selectedIds.value)

        // A progress update on the OTHER row -> it becomes BEING_INSTALLED but stays at its index.
        c.updateDownloadStatus("b", DocInstallStatus.BEING_INSTALLED, 30)

        assertEquals(listOf("a", "b"), c.displayed.value.map { it.docId }) // no re-sort, no float
        val bRow = c.displayed.value.first { it.docId == "b" }
        assertEquals(DocInstallStatus.BEING_INSTALLED, bRow.installStatus)
        assertEquals(30, bRow.percentDone)
        // Selection preserved.
        assertTrue(c.selectionMode.value); assertEquals(setOf("a"), c.selectedIds.value)
    }

    @Test fun updateDownloadStatus_noop_when_unchanged() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE)), null)
        c.setTypeFilter(DocTypeFilter.ALL)
        c.updateDownloadStatus("a", DocInstallStatus.BEING_INSTALLED, 40)
        val before = c.displayed.value
        // Same status+percent -> no change, same list instance (early return).
        c.updateDownloadStatus("a", DocInstallStatus.BEING_INSTALLED, 40)
        assertTrue(before === c.displayed.value)
        // Unknown docId -> no change.
        c.updateDownloadStatus("does-not-exist", DocInstallStatus.INSTALLED, 100)
        assertTrue(before === c.displayed.value)
    }

    /**
     * Classic parity: a download must not move the row. computeDisplayed floats BEING_INSTALLED
     * to the top, so re-sorting on a progress tick made the row jump — the reported defect.
     */
    @Test fun download_progress_keeps_the_row_at_its_index() {
        val c = controller()
        // Sorted by abbreviation within the same status/category, so ids a..e are indexes 0..4.
        c.setDocuments(
            listOf(
                row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE), row("c", DocCategory.BIBLE),
                row("d", DocCategory.BIBLE), row("e", DocCategory.BIBLE),
            ),
            null,
        )
        assertEquals(2, c.displayed.value.indexOfFirst { it.docId == "c" })

        c.updateDownloadStatus("c", DocInstallStatus.BEING_INSTALLED, 0)
        assertEquals(2, c.displayed.value.indexOfFirst { it.docId == "c" })
        c.updateDownloadStatus("c", DocInstallStatus.BEING_INSTALLED, 45)
        assertEquals(2, c.displayed.value.indexOfFirst { it.docId == "c" })
        assertEquals(45, c.displayed.value[2].percentDone)
        c.updateDownloadStatus("c", DocInstallStatus.INSTALLED, 100)
        assertEquals(2, c.displayed.value.indexOfFirst { it.docId == "c" })
        assertEquals(DocInstallStatus.INSTALLED, c.displayed.value[2].installStatus)
    }

    @Test fun download_progress_leaves_result_count_alone() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE)), null)
        assertEquals(2, c.resultCount.value)
        c.updateDownloadStatus("a", DocInstallStatus.BEING_INSTALLED, 10)
        assertEquals(2, c.resultCount.value)
    }

    @Test fun download_progress_for_a_filtered_out_row_updates_documents_only() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.COMMENTARY)), null)
        c.setTypeFilter(DocTypeFilter.BIBLE)
        assertEquals(listOf("a"), c.displayed.value.map { it.docId })

        c.updateDownloadStatus("b", DocInstallStatus.BEING_INSTALLED, 30)

        assertEquals(listOf("a"), c.displayed.value.map { it.docId })
        assertEquals(
            DocInstallStatus.BEING_INSTALLED,
            c.documents.value.first { it.docId == "b" }.installStatus,
        )
    }

    /** The four compositional triggers DO re-sort, so the installing row floats to the top there. */
    @Test fun setDocuments_resorts_and_floats_the_installing_row() {
        val c = controller()
        val rows = listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE), row("c", DocCategory.BIBLE))
        c.setDocuments(rows, null)
        c.updateDownloadStatus("c", DocInstallStatus.BEING_INSTALLED, 20)
        assertEquals(2, c.displayed.value.indexOfFirst { it.docId == "c" })

        // A refresh re-pushes the master list, which is where classic re-sorts too.
        c.setDocuments(c.documents.value, null)
        assertEquals(0, c.displayed.value.indexOfFirst { it.docId == "c" })
    }

    @Test fun changing_a_filter_resorts_and_floats_the_installing_row() {
        val c = controller()
        c.setDocuments(
            listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE), row("c", DocCategory.BIBLE)),
            null,
        )
        c.updateDownloadStatus("c", DocInstallStatus.BEING_INSTALLED, 20)
        assertEquals(2, c.displayed.value.indexOfFirst { it.docId == "c" })

        c.setTypeFilter(DocTypeFilter.BIBLE)
        assertEquals(0, c.displayed.value.indexOfFirst { it.docId == "c" })
    }

    @Test fun download_progress_does_not_clear_an_active_selection() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE)), null)
        c.enterSelection()
        c.toggle("a")

        c.updateDownloadStatus("b", DocInstallStatus.BEING_INSTALLED, 5)

        assertTrue(c.selectionMode.value)
        assertEquals(setOf("a"), c.selectedIds.value)
    }

    /**
     * Pins the property behind the Round-6 fix wave's Important-1 fix: DownloadComposeActivity's
     * refreshRowStatus() (called from doDownload() after downloadControl.downloadDocument() returns,
     * i.e. on both completion AND cancellation) used to call controller.setDocuments(...), which runs
     * refilter() — a full re-sort plus clearSelection(). On cancel the terminal status is NOT_INSTALLED,
     * which always differs from the BEING_INSTALLED mirror, so that path fired deterministically on
     * every cancellation: the row dropped out of the installing position and any active multi-selection
     * was silently wiped. The fix routes refreshRowStatus() through updateDownloadStatus() instead,
     * which updates the row IN PLACE. This test is host-unreachable (refreshRowStatus is a private
     * method on an Activity with a heavy DI/JSword surface, not exercised by any existing unit test
     * harness), so it pins the equivalent controller-level property the fix relies on: a transition
     * back to NOT_INSTALLED (what a cancel produces) via updateDownloadStatus() keeps the row's index
     * and does not clear an active selection.
     */
    @Test fun cancel_keeps_the_row_at_its_index_and_preserves_selection() {
        val c = controller()
        c.setDocuments(
            listOf(row("a", DocCategory.BIBLE), row("b", DocCategory.BIBLE), row("c", DocCategory.BIBLE)),
            null,
        )
        c.enterSelection(); c.toggle("a")
        assertEquals(1, c.displayed.value.indexOfFirst { it.docId == "b" })

        c.updateDownloadStatus("b", DocInstallStatus.BEING_INSTALLED, 30)
        assertEquals(1, c.displayed.value.indexOfFirst { it.docId == "b" })

        // Cancel: getDocumentStatus() reverts to NOT_INSTALLED. This is the exact transition
        // refreshRowStatus() feeds through updateDownloadStatus() after the fix.
        c.updateDownloadStatus("b", DocInstallStatus.NOT_INSTALLED, 0)

        assertEquals(1, c.displayed.value.indexOfFirst { it.docId == "b" }) // row did not move
        assertEquals(DocInstallStatus.NOT_INSTALLED, c.displayed.value[1].installStatus)
        assertTrue(c.selectionMode.value) // selection survived
        assertEquals(setOf("a"), c.selectedIds.value)
    }

    @Test
    fun openSearchDoesNotTouchTheQuery() {
        val c = controller()
        c.setQuery("gen")
        c.openSearch()
        assertTrue(c.searchModeActive.value)
        assertEquals("gen", c.query.value)
    }

    @Test
    fun closeSearchLeavesSearchModeAndClearsTheQuery() {
        val c = controller()
        c.setQuery("gen")
        c.openSearch()
        assertTrue(c.searchModeActive.value)
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.query.value)
    }
}
