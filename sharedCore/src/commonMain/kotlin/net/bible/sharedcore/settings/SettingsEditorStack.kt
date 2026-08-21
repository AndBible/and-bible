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

package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One page of the settings editor sheet. A page names **what** is being edited, never a snapshot of
 * its value: the value is always re-read from the live [SettingsScreenState] /
 * [ColorSettingsUiState] when the page renders, so a stale value is structurally impossible and an
 * async state update while the sheet is open is reflected immediately.
 */
sealed interface SettingsEditorPage {
    /** Edit the settings row with this key; the editor kind comes from the live [SettingsItem]. */
    data class Row(val key: String) : SettingsEditorPage

    /** The whole colours form. */
    data object Colors : SettingsEditorPage

    /** One colour field of the colours form. */
    data class ColorPick(val field: ColorField) : SettingsEditorPage

    /** Background-image chooser for the day (`false`) or night (`true`) half. */
    data class BackgroundImage(val night: Boolean) : SettingsEditorPage
}

/**
 * The settings editor sheet's page stack. Pure and host-agnostic: one instance per host (a settings
 * screen, or the reading view), driving exactly one `SettingsEditorSheet`.
 *
 * [pop] is what the sheet's single `onDismissRequest` calls — back, scrim tap and swipe-down cannot
 * be told apart by Compose Material3, and `BackHandler` cannot live in commonMain, so dismiss steps
 * back one page and closes the sheet only from the first page. That is a deliberate trade-off
 * recorded in the spec; the sheet header carries an explicit close affordance for the other case.
 */
class SettingsEditorStack {
    private val _pages = MutableStateFlow<List<SettingsEditorPage>>(emptyList())
    val pages: StateFlow<List<SettingsEditorPage>> = _pages.asStateFlow()

    val current: SettingsEditorPage? get() = _pages.value.lastOrNull()
    val depth: Int get() = _pages.value.size

    /** Open the sheet at [page], discarding any stack a previous open left behind. */
    fun open(page: SettingsEditorPage) { _pages.value = listOf(page) }

    fun push(page: SettingsEditorPage) { _pages.value = _pages.value + page }

    /** Remove the top page; at depth 1 this closes the sheet. A no-op when already closed. */
    fun pop() { _pages.value = _pages.value.dropLast(1) }

    fun close() { _pages.value = emptyList() }

    /**
     * Close the WHOLE sheet if any page satisfies [predicate] — the vanished-row guard. Closing
     * everything rather than just the offending page is deliberate: a page below the vanished one
     * was reached *through* it, so leaving it open would strand the user on a page whose parent no
     * longer exists.
     */
    fun closeIf(predicate: (SettingsEditorPage) -> Boolean) {
        if (_pages.value.any(predicate)) close()
    }
}
