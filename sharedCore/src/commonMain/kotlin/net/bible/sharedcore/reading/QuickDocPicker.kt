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

/** One candidate document for the quick-switch decision. `id` = the SWORD `Book.initials` (stable). */
data class QuickDocRow(
    val id: String,
    val language: String,
    val abbreviation: String,
)

sealed interface QuickDocAction {
    /** No documents to offer: the caller opens the full chooser typed to the button's scope. */
    object None : QuickDocAction
    /** Exactly two docs: switch straight to the non-active one, no sheet (classic `menuForDocs` :1915-1916). */
    data class SwitchDirectly(val id: String) : QuickDocAction
    /** 1, or 3+ documents: show the scoped document sheet. */
    object ShowPicker : QuickDocAction
}

/**
 * Pure decision for the reading-view quick-document picker, the port of classic
 * `MainBibleActivity.menuForDocs` (`page/MainBibleActivity.kt:1905-1924`) and iOS's
 * `BibleReaderQuickModuleSelectorPresentation.action`. Sorts by language then abbreviation
 * (classic `compareBy({language.code},{abbreviation})`) and applies the 2-doc direct-switch
 * shortcut. The sheet's `DocumentQuickContent` draws the current row bold and inert.
 */
object QuickDocPicker {
    fun action(rows: List<QuickDocRow>, activeId: String): QuickDocAction {
        val sorted = rows.sortedWith(compareBy({ it.language }, { it.abbreviation }))
        return when (sorted.size) {
            0 -> QuickDocAction.None
            2 -> QuickDocAction.SwitchDirectly((sorted.firstOrNull { it.id != activeId } ?: sorted.first()).id)
            else -> QuickDocAction.ShowPicker
        }
    }
}
