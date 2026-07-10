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

    @Test fun updateDownloadStatus_floats_being_installed_and_preserves_selection() {
        val c = controller()
        c.setDocuments(listOf(row("a", DocCategory.BIBLE, abbr = "a"), row("b", DocCategory.BIBLE, abbr = "b")), null)
        c.setTypeFilter(DocTypeFilter.ALL)
        assertEquals(listOf("a", "b"), c.displayed.value.map { it.docId })
        // Enter selection and select "a" — a progress tick must NOT wipe this.
        c.enterSelection(); c.toggle("a")
        assertTrue(c.selectionMode.value); assertEquals(setOf("a"), c.selectedIds.value)

        // A progress update on the OTHER row -> it becomes BEING_INSTALLED and floats to the top.
        c.updateDownloadStatus("b", DocInstallStatus.BEING_INSTALLED, 30)

        assertEquals(listOf("b", "a"), c.displayed.value.map { it.docId }) // being-installed floats first
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
}
