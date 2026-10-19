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

package net.bible.android.view.activity.page

import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.service.common.CommonUtils
import net.bible.sharedui.strings.PaneMenuStrings

/** [PaneMenuStrings] over the Android string resources (the iOS twin is `KeyedPaneMenuStrings`, by the same keys). */
class AndroidPaneMenuStrings : PaneMenuStrings {
    private fun s(id: Int) = application.getString(id)
    override val newWindow get() = s(R.string.new_window)
    override val windowMaximise get() = s(R.string.windowMaximise)
    override val windowMinimise get() = s(R.string.windowMinimise)
    override val changeToNormal get() = s(R.string.change_to_normal)
    override val moveWindow get() = s(R.string.move_window)
    override val windowPinMode get() = s(R.string.window_pin_mode)
    override val windowSynchronise get() = s(R.string.windowSynchronise)
    override val addWholePageBookmark get() = s(R.string.add_whole_page_bookmark)
    override val textOptionsWindowMenuTitle get() = s(R.string.text_options_window_menutitle)
    override val allTextOptionsWindowMenuTitle get() = s(R.string.all_text_options_window_menutitle)
    override val copySettings get() = s(R.string.copy_settings)
    override val copyReference get() = s(R.string.copyReference)
    override val close get() = s(R.string.close)
    override val disableSync get() = s(R.string.disable_sync)
    override val studypad get() = s(R.string.studypad)
    override val llmActions get() = s(R.string.llm_actions)
    override val copySettingsToWorkspace get() = s(R.string.copy_settings_to_workspace)
    override val copySettingsToGlobal get() = s(R.string.copy_settings_to_global)
    override val referenceCopiedToClipboard get() = s(R.string.reference_copied_to_clipboard)

    override fun exportFileFormat(format: String) = application.getString(R.string.export_fileformat, format)
    override fun exportSomething(what: String) = application.getString(R.string.export_something, what)
    override fun exportBookmarksCsv(format: String) = application.getString(R.string.export_bookmarks_csv, format)
    override fun goToRef(name: String?) = application.getString(R.string.go_to_ref, name)
    override fun moveWindowToPosition(position: Int, abbreviation: String?, key: String?) =
        application.getString(R.string.move_window_to_position2, position, abbreviation, key)
    override fun syncGroupN(n: Int) = application.getString(R.string.sync_group_n, n)
    override fun copySettingsToWindow(position: Int, abbreviation: String?, key: String?) =
        application.getString(R.string.copy_settings_to_window, position, abbreviation, key)
}

/** Android's pane menu builder: the common one over Android strings, `getPrefItem` items and `CommonUtils` state. */
fun androidWindowPaneMenuStateBuilder(windowControl: WindowControl, speakControl: SpeakControl) =
    WindowPaneMenuStateBuilder(
        windowControl = windowControl,
        speakControl = speakControl,
        strings = AndroidPaneMenuStrings(),
        prefItems = net.bible.android.platform.AndroidTextDisplayPrefItems(),
        recentDisplaySettings = { CommonUtils.lastDisplaySettingsSorted },
        globalTextDisplaySettings = { CommonUtils.globalTextDisplaySettings },
        llmConfigured = { CommonUtils.settings.llmConfigured },
    )
