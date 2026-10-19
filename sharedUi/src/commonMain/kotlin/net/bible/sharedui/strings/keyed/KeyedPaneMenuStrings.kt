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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

import net.bible.sharedui.strings.PaneMenuStrings

/** [PaneMenuStrings] over a [StringTable]: the resource keys [KEYS] (all in `strings.xml`) that `AndroidPaneMenuStrings` reads. */
@kotlin.native.HiddenFromObjC
class KeyedPaneMenuStrings(private val t: StringTable) : PaneMenuStrings {
    override val newWindow get() = t.text("new_window")
    override val windowMaximise get() = t.text("windowMaximise")
    override val windowMinimise get() = t.text("windowMinimise")
    override val changeToNormal get() = t.text("change_to_normal")
    override val moveWindow get() = t.text("move_window")
    override val windowPinMode get() = t.text("window_pin_mode")
    override val windowSynchronise get() = t.text("windowSynchronise")
    override val addWholePageBookmark get() = t.text("add_whole_page_bookmark")
    override val textOptionsWindowMenuTitle get() = t.text("text_options_window_menutitle")
    override val allTextOptionsWindowMenuTitle get() = t.text("all_text_options_window_menutitle")
    override val copySettings get() = t.text("copy_settings")
    override val copyReference get() = t.text("copyReference")
    override val close get() = t.text("close")
    override val disableSync get() = t.text("disable_sync")
    override val studypad get() = t.text("studypad")
    override val llmActions get() = t.text("llm_actions")
    override val copySettingsToWorkspace get() = t.text("copy_settings_to_workspace")
    override val copySettingsToGlobal get() = t.text("copy_settings_to_global")
    override val referenceCopiedToClipboard get() = t.text("reference_copied_to_clipboard")

    override fun exportFileFormat(format: String) = t.fmt("export_fileformat", format)
    override fun exportSomething(what: String) = t.fmt("export_something", what)
    override fun exportBookmarksCsv(format: String) = t.fmt("export_bookmarks_csv", format)
    override fun goToRef(name: String?) = t.fmt("go_to_ref", name)
    override fun moveWindowToPosition(position: Int, abbreviation: String?, key: String?) =
        t.fmt("move_window_to_position2", position, abbreviation, key)
    override fun syncGroupN(n: Int) = t.fmt("sync_group_n", n)
    override fun copySettingsToWindow(position: Int, abbreviation: String?, key: String?) =
        t.fmt("copy_settings_to_window", position, abbreviation, key)

    companion object {
        /** Every resource key this class reads (pinned against `strings.xml` by `KeyedPaneMenuStringsTest`). */
        val KEYS = listOf(
            "new_window", "windowMaximise", "windowMinimise", "change_to_normal", "move_window", "window_pin_mode",
            "windowSynchronise", "add_whole_page_bookmark", "text_options_window_menutitle",
            "all_text_options_window_menutitle", "copy_settings", "copyReference", "close", "disable_sync", "studypad",
            "llm_actions", "copy_settings_to_workspace", "copy_settings_to_global", "reference_copied_to_clipboard",
            "export_fileformat", "export_something", "export_bookmarks_csv", "go_to_ref", "move_window_to_position2",
            "sync_group_n", "copy_settings_to_window",
        )
    }
}
