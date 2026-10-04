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

/** The document quick sheet's tabs, in display order. A sheet shows LAST_FILTER or ALL, never both (see [DocumentSheetScope]). */
enum class DocumentQuickTab { RECENT, FOR_VERSE, LAST_FILTER, ALL }

/**
 * Which documents a document quick sheet offers. [ALL] is the title row's long-press sheet;
 * [BIBLE] and [COMMENTARY] are the toolbar buttons' sheets. The Commentary button has always also
 * listed general books and dictionaries (classic `menuForDocs` parity), so its scope does too.
 *
 * @property categories the categories admitted, `null` = no restriction
 * @property chooserType the `ChooseDocument` type argument the sheet's footer and its empty-scope
 *   fallback open (`NavRoutes.chooseDocument(type)`), `null` = untyped
 * @property tabSettingKey the setting holding this sheet's last-picked tab id; [ALL]'s is the key
 *   the title-row sheet has always used, so existing users keep their saved tab
 */
enum class DocumentSheetScope(
    val categories: Set<DocCategory>?,
    val chooserType: String?,
    val tabSettingKey: String,
) {
    ALL(null, null, "document_quick_tab"),
    BIBLE(setOf(DocCategory.BIBLE), "BIBLE", "document_quick_tab_bible"),
    COMMENTARY(
        setOf(DocCategory.COMMENTARY, DocCategory.GENERAL_BOOK, DocCategory.DICTIONARY),
        "COMMENTARY",
        "document_quick_tab_commentary",
    );

    fun admits(row: DocRow): Boolean = categories == null || row.category in categories
}

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
 * A QUICK SHEET MAY ONLY OFFER ROWS IT CAN ACTION, so this function DROPS two kinds of document from
 * [installed] before building any tab (spec §2, which lists unlocking among the things that must not
 * appear in a sheet):
 *
 *  * LOCKED modules — the full ChooseDocument screen answers a tap with an unlock prompt; a sheet
 *    has no such affordance, so offering one would switch the reading view to an undecryptable
 *    document with no route back to unlocking it.
 *  * [DocCategory.AND_BIBLE] pseudo-documents — selecting one is a no-op, which is why the full
 *    screen returns early for them too.
 *
 * "Which documents a quick sheet may offer" is a CONTENT RULE OF THE SHEET, not a fact about any one
 * host's book list, which is why it lives here beside the visibility rule rather than in the caller
 * that happens to assemble the rows — and why it is applied to every tab's source, not only to the
 * filter-derived one. The MRU and `forVerseIds` cannot carry such a row today, but a safety rule
 * must not depend on its callers' guarantees. Visibility is unchanged by this: a tab whose only rows
 * were dropped simply has no rows, and so is hidden.
 *
 * @param installed every installed document, as the host already models them; rows the sheet cannot
 *   action (locked, [DocCategory.AND_BIBLE]) are dropped here rather than by the caller
 * @param recentInitials the MRU, most-recent-first; entries no longer installed are dropped
 * @param forVerseIds documents that contain the current verse; the caller passes the verse's Bibles for
 *   the Bible scope, its commentaries for the Commentary scope, and both for [DocumentSheetScope.ALL]
 * @param lastLanguage / @param lastTypeFilter what `ChooseDocument` persisted last
 * @param scope restricts every tab to the scope's categories; a scoped sheet offers [DocumentQuickTab.ALL] in place
 *   of [DocumentQuickTab.LAST_FILTER], whose own type filter would fight the scope
 */
fun buildDocumentQuickTabs(
    installed: List<DocRow>,
    recentInitials: List<String>,
    forVerseIds: Set<String>,
    lastLanguage: LangOption?,
    lastTypeFilter: DocTypeFilter,
    scope: DocumentSheetScope = DocumentSheetScope.ALL,
): DocumentQuickTabs {
    val offerable = installed
        .filterNot { it.locked || it.category == DocCategory.AND_BIBLE }
        .filter(scope::admits)
    val byId = offerable.associateBy { it.docId }
    val recent = recentInitials.mapNotNull { byId[it] }
    val forVerse = offerable.filter { it.docId in forVerseIds }
    val scoped = scope != DocumentSheetScope.ALL
    val lastFilter = if (scoped) emptyList()
        else computeDisplayedDocuments(offerable, lastLanguage, lastTypeFilter, query = "")
    val all = if (scoped) offerable.sortedWith(compareBy({ it.language.code }, { it.abbreviation }))
        else emptyList()
    val rows = mapOf(
        DocumentQuickTab.RECENT to recent,
        DocumentQuickTab.FOR_VERSE to forVerse,
        DocumentQuickTab.LAST_FILTER to lastFilter,
        DocumentQuickTab.ALL to all,
    )
    return DocumentQuickTabs(
        visible = DocumentQuickTab.entries.filter { rows.getValue(it).isNotEmpty() },
        rowsByTab = rows,
    )
}
