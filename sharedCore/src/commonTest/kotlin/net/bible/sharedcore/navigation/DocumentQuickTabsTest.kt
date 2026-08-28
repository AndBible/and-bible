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

private fun row(
    id: String,
    cat: DocCategory = DocCategory.BIBLE,
    lang: String = "en",
    locked: Boolean = false,
) = DocRow(
    docId = id, osisId = id, abbreviation = id, name = "Doc $id",
    language = LangOption(lang, lang, lang), repository = "r", category = cat,
    installStatus = DocInstallStatus.INSTALLED, percentDone = 100, recommended = false,
    badWarn = false, locked = locked, enciphered = false, canDelete = true, installSizeMb = null,
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

    // --- Rows the sheet cannot action are never offered (spec §2) ---------------------------

    /**
     * A locked module is offered by NO tab, even when every input insists on it — it is in the MRU,
     * it is in `forVerseIds`, and the type filter admits it. A quick sheet has no unlock affordance,
     * so a tap would strand the reading view on an undecryptable document.
     */
    @Test fun aLockedDocumentIsNeverOfferedInAnyTab() {
        val withLocked = installed + row("SECRET", locked = true)
        val tabs = buildDocumentQuickTabs(
            withLocked, listOf("SECRET", "KJV"), setOf("SECRET", "MHC"), null, DocTypeFilter.ALL,
        )
        DocumentQuickTab.entries.forEach { tab ->
            assertFalse(
                "SECRET" in tabs.rowsByTab.getValue(tab).map { it.docId },
                "$tab offers a locked document the sheet cannot unlock",
            )
        }
    }

    /**
     * An AND_BIBLE addon is likewise never offered — selecting one is a no-op.
     *
     * Exercised through a persisted [DocTypeFilter.ADDON], deliberately: [DocTypeFilter.ALL] already
     * excludes addons, so an ALL-based version of this test would pass against an implementation
     * that does no filtering at all and would prove nothing.
     */
    @Test fun anAndBibleAddonIsNeverOfferedUnderAPersistedAddonFilter() {
        val withAddon = installed + row("ADDON", cat = DocCategory.AND_BIBLE)
        val tabs = buildDocumentQuickTabs(
            withAddon, listOf("ADDON"), setOf("ADDON"), null, DocTypeFilter.ADDON,
        )
        DocumentQuickTab.entries.forEach { tab ->
            assertFalse(
                "ADDON" in tabs.rowsByTab.getValue(tab).map { it.docId },
                "$tab offers an AND_BIBLE addon that cannot be opened",
            )
        }
    }

    /**
     * Dropping a row is not the same as emptying a tab into view: a tab whose ONLY row was
     * unactionable must be HIDDEN, exactly like a tab that never had rows.
     */
    @Test fun aTabWhoseOnlyRowWasLockedIsHiddenNotEmpty() {
        val withLocked = installed + row("SECRET", locked = true)
        val tabs = buildDocumentQuickTabs(
            withLocked, listOf("SECRET"), setOf("SECRET"), null, DocTypeFilter.MAPS,
        )
        assertFalse(DocumentQuickTab.RECENT in tabs.visible, "Recent held only a locked document")
        assertFalse(DocumentQuickTab.FOR_VERSE in tabs.visible, "For-verse held only a locked document")
        assertTrue(tabs.visible.isEmpty(), "no offerable recents, no offerable verse docs, no maps installed")
    }
}
