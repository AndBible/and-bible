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

/** The document quick sheet's three tabs (spec §4.5), in display order. */
enum class DocumentQuickTab { RECENT, FOR_VERSE, LAST_FILTER }

/** [visible] is the tabs that have rows, in enum order; [rowsByTab] holds every tab's rows. */
data class DocumentQuickTabs(
    val visible: List<DocumentQuickTab>,
    val rowsByTab: Map<DocumentQuickTab, List<DocRow>>,
)

/**
 * Build the document quick sheet's tabs (spec §4.5).
 *
 * An EMPTY TAB IS HIDDEN, not shown empty. That is what makes the sheet useful on a fresh install,
 * where there is no MRU yet and "For this verse" has to carry it — and it is the property most
 * likely to be lost in a later edit, so [DocumentQuickTabsTest] asserts it directly.
 *
 * @param installed every installed document, as the host already models them
 * @param recentInitials the MRU, most-recent-first; entries no longer installed are dropped
 * @param forVerseIds documents that contain the current verse (the host's `biblesForVerse` +
 *   `commentariesForVerse`, which the toolbar's quick pickers already compute)
 * @param lastLanguage / @param lastTypeFilter what `ChooseDocument` persisted last
 */
fun buildDocumentQuickTabs(
    installed: List<DocRow>,
    recentInitials: List<String>,
    forVerseIds: Set<String>,
    lastLanguage: LangOption?,
    lastTypeFilter: DocTypeFilter,
): DocumentQuickTabs {
    val byId = installed.associateBy { it.docId }
    val recent = recentInitials.mapNotNull { byId[it] }
    val forVerse = installed.filter { it.docId in forVerseIds }
    val lastFilter = computeDisplayedDocuments(installed, lastLanguage, lastTypeFilter, searchIds = null)
    val rows = mapOf(
        DocumentQuickTab.RECENT to recent,
        DocumentQuickTab.FOR_VERSE to forVerse,
        DocumentQuickTab.LAST_FILTER to lastFilter,
    )
    return DocumentQuickTabs(
        visible = DocumentQuickTab.entries.filter { rows.getValue(it).isNotEmpty() },
        rowsByTab = rows,
    )
}
