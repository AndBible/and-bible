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

import net.bible.sharedcore.ui.SheetPageStack

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
 *
 * The mechanics live in [net.bible.sharedcore.ui.SheetPageStack] (round 13a), shared with the Speak
 * sheet; this subclass exists so 12c's call sites and tests keep their concrete type.
 */
class SettingsEditorStack : SheetPageStack<SettingsEditorPage>()

/**
 * Which editor page a text-display-settings row opens; `null` = the key navigates rather than
 * edits, so it is not a sheet page (the two parent-scope drill-up links, and HIDELABELS, which
 * bridges out to the ManageLabels screen).
 *
 * This is the *text-display-settings* routing table, not a general one. Every other
 * `AbSettingsScreen`-based screen opens `SettingsEditorPage.Row(key)` directly, because the editor
 * kind is already declared by the `SettingsItem` the row was built from (`ListChoiceRow`,
 * `TextInputRow`, `MultiSelectRow`). Text display settings need a table because FONTSIZE /
 * TOPMARGIN / LINE_SPACING / MARGINSIZE / COLORS are modelled as `NavigationRow`s, so their editor
 * is not derivable from the row kind.
 *
 * Extracted from `TextDisplaySettingsScreen.handleNavigate` so it is unit-testable and so the
 * reading view's in-place editor routes identically to the settings screen — one table, two
 * surfaces.
 */
fun textSettingEditorPageFor(key: String): SettingsEditorPage? {
    if (key == KEY_OPEN_WORKSPACE_SETTINGS || key == KEY_OPEN_GLOBAL_SETTINGS) return null
    val type = TextSettingType.entries.firstOrNull { it.name == key } ?: return null
    return when (type) {
        TextSettingType.COLORS -> SettingsEditorPage.Colors
        TextSettingType.FONTSIZE,
        TextSettingType.TOPMARGIN,
        TextSettingType.LINE_SPACING,
        TextSettingType.MARGINSIZE,
        TextSettingType.FONTFAMILY,
        TextSettingType.STRONGS,
        TextSettingType.PAGE_SCROLL_AMOUNT,
        TextSettingType.SCROLL_HELPER_LINE_STYLE -> SettingsEditorPage.Row(type.name)
        else -> null
    }
}
