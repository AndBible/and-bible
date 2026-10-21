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

package net.bible.sharedui.reading.toolbar

import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedui.strings.keyed.KeyedReadingMenuStrings

/** What the reading view's overflow menu reads and does on the platform; the iOS implementation is `IosReadingToolbarHost`. */
@kotlin.native.HiddenFromObjC
interface ReadingOverflowHost {
    /** Flips full-screen reading (Android: `SharedActivityState.toggleFullScreen`). */
    fun toggleFullScreen()

    fun nightMode(): Boolean

    /** Whether the user may switch night mode by hand (Android: only in its manual night mode). */
    fun nightModeSwitchable(): Boolean
    fun setNightMode(on: Boolean)

    /** Whether the device can sense tilt at all (the row is hidden otherwise). */
    fun tiltPossible(): Boolean
    fun tiltToScroll(): Boolean
    fun setTiltToScroll(on: Boolean)

    fun isMultiWindow(): Boolean
    fun reverseSplit(): Boolean

    /** Sets reversed split mode and re-lays the windows out. */
    fun setReverseSplit(on: Boolean)

    /** The automatic window pinning workspace setting. */
    fun autoPin(): Boolean

    /** Sets it and applies it to the windows (Android's `autoPinChanged`). */
    fun setAutoPin(on: Boolean)

    /** Whether an LLM is configured, which shows the "AI Actions" row (Android: `CommonUtils.settings.llmConfigured`). */
    fun llmConfigured(): Boolean

    /** Opens the prompt selector for the workspace menu (Android: `PromptContext.WORKSPACE_MENU`, no selection). */
    fun showLlmActions()

    /**
     * Opens the workspace label manager for the auto-assign labels and applies its answer (Android's
     * `AutoAssignPreference.openDialog`). Returns at once; the answer arrives later.
     */
    fun editAutoAssignLabels()

    /** The recently used text display settings, sorted as Android's last-used menu rows, as workspace-level items. */
    fun recentTextOptions(): List<Pair<TextDisplaySettings.Types, PrefItemHandle>>

    fun showWorkspaceSheet()
    fun openAllTextOptions()
    fun workspaceScope(): SettingsScope
    fun showTextSettingEditor(scope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit)

    /** Pushes a changed workspace text setting to every window (Android's `updateAllWindowsTextDisplaySettings`). */
    fun applyWorkspaceTextSettings(types: Set<TextDisplaySettings.Types>)
}

/**
 * The overflow ("3-dot") menu's items and click handling, after Android's `OptionsMenuStateBuilder`: the rows in
 * Android's rendered order, the workspace-level recently used text settings, then "all text options".
 *
 * A stand-in, not an omission: a recent text option that is neither a toggle nor sheet-editable (Android calls its
 * `openDialog`) opens the full text options screen instead.
 */
@kotlin.native.HiddenFromObjC
class ReadingOverflowController(private val host: ReadingOverflowHost, private val strings: () -> KeyedReadingMenuStrings) {

    fun build(): List<OptionsMenuItem> {
        val s = strings()
        val items = mutableListOf<OptionsMenuItem>()
        items += OptionsMenuItem(
            ID_FULLSCREEN, s.toggleFullscreen, checkable = false, checked = false, enabled = true,
            opensDialog = false, iconKey = "ic_full_screen_24",
        )
        items += OptionsMenuItem(
            ID_NIGHT_MODE, s.nightMode, checkable = true, checked = host.nightMode(), enabled = host.nightModeSwitchable(),
            opensDialog = false, iconKey = "ic_night_mode_24",
        )
        items += OptionsMenuItem(
            ID_SWITCH_TO_WORKSPACE, s.switchToWorkspace, checkable = false, checked = false, enabled = true,
            opensDialog = true, iconKey = "ic_baseline_workspace_24",
        )
        if (host.tiltPossible()) {
            items += OptionsMenuItem(
                ID_TILT_TO_SCROLL, s.tiltToScroll, checkable = true, checked = host.tiltToScroll(), enabled = true,
                opensDialog = false, iconKey = "ic_tilt_to_scroll_24dp",
            )
        }
        if (host.isMultiWindow()) {
            items += OptionsMenuItem(
                ID_SPLIT_MODE, s.reversedSplitMode, checkable = true, checked = host.reverseSplit(), enabled = true,
                opensDialog = false, iconKey = "ic_reverse_split_mode_24dp",
            )
        }
        items += OptionsMenuItem(
            ID_AUTO_PIN_MODE, s.windowPinning, checkable = true, checked = !host.autoPin(), enabled = true,
            opensDialog = false, iconKey = "ic_window_pinning_24",
        )
        items += OptionsMenuItem(
            ID_AUTO_ASSIGN_LABELS, s.autoAssignLabels, checkable = false, checked = false, enabled = true,
            opensDialog = true, iconKey = "ic_label_settings_24",
        )
        if (host.llmConfigured()) {
            items += OptionsMenuItem(
                ID_LLM_ACTIONS, s.llmActions, checkable = false, checked = false, enabled = true,
                opensDialog = true, iconKey = "icon_robot",
            )
        }
        var firstOfSection = true
        host.recentTextOptions().forEachIndexed { order, (_, item) ->
            if (!(item.enabled && item.visible)) return@forEachIndexed
            items += OptionsMenuItem(
                id = "$TEXT_OPTION_PREFIX$order",
                label = item.title ?: "",
                checkable = item.isBoolean,
                checked = item.value == true,
                enabled = item.enabled,
                opensDialog = item.opensDialog,
                iconKey = item.iconKey,
                startsNewSection = firstOfSection,
            )
            firstOfSection = false
        }
        items += OptionsMenuItem(
            ID_ALL_TEXT_OPTIONS, s.allTextOptions, checkable = false, checked = false, enabled = true, opensDialog = true,
            iconKey = "ic_text_options_24dp", startsNewSection = firstOfSection,
        )
        return items
    }

    /** Runs a clicked row; true keeps the menu open (a toggle: the host rebuilds the list to show the flipped check). */
    fun handle(id: String): Boolean {
        when (id) {
            ID_FULLSCREEN -> host.toggleFullScreen()
            ID_NIGHT_MODE -> {
                if (!host.nightModeSwitchable()) return true
                host.setNightMode(!host.nightMode())
                return true
            }
            ID_SWITCH_TO_WORKSPACE -> host.showWorkspaceSheet()
            ID_TILT_TO_SCROLL -> { host.setTiltToScroll(!host.tiltToScroll()); return true }
            ID_SPLIT_MODE -> { host.setReverseSplit(!host.reverseSplit()); return true }
            ID_AUTO_PIN_MODE -> { host.setAutoPin(!host.autoPin()); return true }
            ID_AUTO_ASSIGN_LABELS -> host.editAutoAssignLabels()
            ID_LLM_ACTIONS -> host.showLlmActions()
            ID_ALL_TEXT_OPTIONS -> host.openAllTextOptions()
            else -> if (id.startsWith(TEXT_OPTION_PREFIX)) return handleTextOption(id.removePrefix(TEXT_OPTION_PREFIX).toIntOrNull())
        }
        return false
    }

    private fun handleTextOption(order: Int?): Boolean {
        val (type, item) = order?.let { host.recentTextOptions().getOrNull(it) } ?: return false
        if (item.isBoolean) {
            item.value = item.value != true
            item.handle()
            host.applyWorkspaceTextSettings(setOf(type))
            return true
        }
        // A setting with an in-place editor sheet opens it; any other opens the full text options screen.
        val page = textSettingEditorPageFor(type.name)
        if (page == null) {
            host.openAllTextOptions()
        } else {
            host.showTextSettingEditor(host.workspaceScope(), page) { host.applyWorkspaceTextSettings(setOf(type)) }
        }
        return false
    }

    companion object {
        const val ID_FULLSCREEN = "fullscreen"
        const val ID_NIGHT_MODE = "nightMode"
        const val ID_SWITCH_TO_WORKSPACE = "switchToWorkspace"
        const val ID_TILT_TO_SCROLL = "tiltToScroll"
        const val ID_SPLIT_MODE = "splitMode"
        const val ID_AUTO_PIN_MODE = "autoPinMode"
        const val ID_AUTO_ASSIGN_LABELS = "autoAssignLabels"
        const val ID_LLM_ACTIONS = "llmActionsSubMenu"
        const val ID_ALL_TEXT_OPTIONS = "allTextOptions"
        const val TEXT_OPTION_PREFIX = "textOptionItem:"
    }
}
