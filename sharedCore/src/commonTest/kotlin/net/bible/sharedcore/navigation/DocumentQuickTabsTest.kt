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
import kotlin.test.assertTrue

private fun row(id: String, cat: DocCategory = DocCategory.BIBLE, lang: String = "en") = DocRow(
    docId = id, osisId = id, abbreviation = id, name = "Doc $id",
    language = LangOption(lang, lang, lang), repository = "r", category = cat,
    installStatus = DocInstallStatus.INSTALLED, percentDone = 100, recommended = false,
    badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = null,
)

class DocumentQuickTabsTest {
    private val installed = listOf(
        row("KJV"), row("ESV"), row("MHC", DocCategory.COMMENTARY),
        row("STRONG", DocCategory.DICTIONARY), row("FIN", lang = "fi"),
    )

    @Test fun recentIsHiddenWhenThereIsNoHistoryYet() {
        val tabs = buildDocumentQuickTabs(installed, emptyList(), setOf("KJV"), null, DocTypeFilter.ALL)
        assertFalse(DocumentQuickTab.RECENT in tabs.visible, "a fresh install must not show an empty Recent tab")
        assertEquals(DocumentQuickTab.FOR_VERSE, tabs.visible.first())
    }

    @Test fun recentPreservesTheMruOrderAndDropsUninstalledEntries() {
        val tabs = buildDocumentQuickTabs(installed, listOf("ESV", "GONE", "KJV"), emptySet(), null, DocTypeFilter.ALL)
        assertEquals(listOf("ESV", "KJV"), tabs.rowsByTab.getValue(DocumentQuickTab.RECENT).map { it.docId })
    }

    @Test fun forVerseCarriesExactlyTheSuppliedIds() {
        val tabs = buildDocumentQuickTabs(installed, listOf("KJV"), setOf("MHC", "KJV"), null, DocTypeFilter.ALL)
        assertEquals(setOf("KJV", "MHC"), tabs.rowsByTab.getValue(DocumentQuickTab.FOR_VERSE).map { it.docId }.toSet())
    }

    @Test fun lastFilterAppliesBothLanguageAndType() {
        val tabs = buildDocumentQuickTabs(
            installed, listOf("KJV"), setOf("KJV"),
            LangOption("en", "English", "en"), DocTypeFilter.COMMENTARY,
        )
        assertEquals(listOf("MHC"), tabs.rowsByTab.getValue(DocumentQuickTab.LAST_FILTER).map { it.docId })
    }

    @Test fun aTabWithNoRowsIsHidden() {
        val tabs = buildDocumentQuickTabs(installed, emptyList(), emptySet(), null, DocTypeFilter.MAPS)
        assertTrue(tabs.visible.isEmpty(), "no recents, no verse docs, no maps installed")
    }

    @Test fun everyVisibleTabHasRows() {
        val tabs = buildDocumentQuickTabs(installed, listOf("KJV"), setOf("MHC"), null, DocTypeFilter.ALL)
        tabs.visible.forEach { tab ->
            assertTrue(tabs.rowsByTab.getValue(tab).isNotEmpty(), "$tab is visible but empty")
        }
    }
}
