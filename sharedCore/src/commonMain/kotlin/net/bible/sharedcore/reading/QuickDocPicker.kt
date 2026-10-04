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

package net.bible.sharedcore.reading

import net.bible.sharedcore.navigation.DocCategory

/**
 * One selectable document in the quick-switch picker. `id` = the SWORD `Book.initials` (stable).
 * [category] drives the row's leading icon: the Commentary button's menu deliberately also lists
 * general books and dictionaries (classic parity), so the icon is what makes the mixture legible.
 */
data class QuickDocRow(
    val id: String,
    val label: String,
    val language: String,
    val abbreviation: String,
    val category: DocCategory,
)

/** A rendered popup row: [enabled] is false for the current document (visible-but-disabled, classic parity). */
data class QuickDocMenuItem(val id: String, val label: String, val enabled: Boolean, val category: DocCategory)

sealed interface QuickDocAction {
    /** No documents to offer — show nothing (classic would show an empty popup; None is the clean equivalent). */
    object None : QuickDocAction
    /** Exactly two docs: switch straight to the non-active one, no menu (classic `menuForDocs` :1915-1916). */
    data class SwitchDirectly(val id: String) : QuickDocAction
    /** Show the anchored dropdown with these rows (already sorted; current disabled). */
    data class ShowPopup(val items: List<QuickDocMenuItem>) : QuickDocAction
}

/**
 * Pure decision for the reading-view quick-document picker — the port of classic
 * `MainBibleActivity.menuForDocs` (`page/MainBibleActivity.kt:1905-1924`) and iOS's
 * `BibleReaderQuickModuleSelectorPresentation.action`. Sorts by language then abbreviation
 * (classic `compareBy({language.code},{abbreviation})`), applies the 2-doc direct-switch shortcut,
 * and marks the current document disabled-but-visible.
 */
object QuickDocPicker {
    fun action(rows: List<QuickDocRow>, activeId: String): QuickDocAction {
        val sorted = rows.sortedWith(compareBy({ it.language }, { it.abbreviation }))
        return when (sorted.size) {
            0 -> QuickDocAction.None
            2 -> QuickDocAction.SwitchDirectly((sorted.firstOrNull { it.id != activeId } ?: sorted.first()).id)
            else -> QuickDocAction.ShowPopup(
                sorted.map { QuickDocMenuItem(it.id, it.label, enabled = it.id != activeId, category = it.category) },
            )
        }
    }
}
