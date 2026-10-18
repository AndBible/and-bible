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

package net.bible.sharedui.ios.reading

import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.reading.session.ConfigInputs
import net.bible.sharedui.reading.session.DocumentLoaderPlatform

/**
 * iOS [DocumentLoaderPlatform] for one reading window.
 *
 * Offsets are 0: [net.bible.sharedui.reading.host.ReadingHost] lays the pane out below the toolbar and inside the
 * safe area, so the WebView does not extend under them (Android's BibleView does, hence its non-zero offsets).
 * [lastUpdated] stays 0, so every load starts at the page manager's current location.
 *
 * `themeColors` is `"null"` (BibleView's own palette): `themeColorsJson` lives in `:app`; moving it is Stream R/X.
 * Settings are read when `set_config` is built, never at construction.
 */
@kotlin.native.HiddenFromObjC
class IosDocumentLoaderPlatform(
    private val window: () -> Window,
    private val windowControl: () -> WindowControl,
    private val settings: () -> AppSettings,
    private val nightModeState: () -> NightModeState,
    private val llmConfigured: () -> Boolean = { false },
) : DocumentLoaderPlatform {
    override val lastUpdated: Long get() = 0L
    override val topOffset: Float get() = 0f
    override val bottomOffset: Float get() = 0f

    private val repository get() = windowControl().windowRepository

    private val isActive get() = windowControl().activeWindow.id == window().id

    override val hasActiveIndicator: Boolean
        get() = settings().getBoolean("show_active_window_indicator", true) && isActive && repository.visibleWindows.size > 1

    override fun configInputs(favouriteLabelIds: List<IdType>): ConfigInputs {
        val s = settings()
        val w = window()
        val workspaceSettings = repository.workspaceSettings
        val colorMode = DisplayColorMode.fromValue(s.getString("display_color_mode", null)) ?: DisplayColorMode.NORMAL
        return ConfigInputs(
            displaySettings = w.pageManager.actualTextDisplaySettings,
            isActive = isActive,
            isBottomWindow = repository.lastVisibleWindow == w,
            hasActiveIndicator = hasActiveIndicator,
            nightMode = nightModeState().nightMode,
            showErrorBox = false,
            favouriteLabels = favouriteLabelIds,
            recentLabels = workspaceSettings.recentLabels.map { it.labelId },
            studyPadCursors = workspaceSettings.studyPadCursors.toMap(),
            autoAssignLabels = workspaceSettings.autoAssignLabels.toList(),
            hideCompareDocuments = workspaceSettings.hideCompareDocuments.toSet(),
            limitAmbiguousModalSize = workspaceSettings.limitAmbiguousModalSize,
            windowDisplayId = w.displayId,
            disableBibleModalButtons = s.getStringSet("disable_bible_bookmark_modal_buttons", emptySet()),
            disableGenericModalButtons = s.getStringSet("disable_gen_bookmark_modal_buttons", emptySet()),
            monochromeMode = colorMode != DisplayColorMode.NORMAL,
            einkMode = s.getBoolean("eink_mode", false),
            colorEinkMode = colorMode == DisplayColorMode.COLOR_EINK,
            pureMonochromeMode = colorMode == DisplayColorMode.MONOCHROME,
            disableAnimations = s.getBoolean("disable_animations", false),
            fontSizeMultiplier = s.getInt("font_size_multiplier", 100) / 100F,
            enabledExperimentalFeatures = s.getStringSet("experimental_features", emptySet()).toList(),
            themeColors = "null",
            disableClickToEdit = s.getBoolean("disable_click_to_edit", false),
            llmConfigured = llmConfigured(),
            notesContentType = s.getString("notes_content_type", "HTML") ?: "HTML",
        )
    }

    // Pending: always true; sessions are not rebound when a Window object is re-created from the database.
    override fun isCurrentView(): Boolean = true

    override fun enableZoomForMap(isMap: Boolean) = Unit
}
