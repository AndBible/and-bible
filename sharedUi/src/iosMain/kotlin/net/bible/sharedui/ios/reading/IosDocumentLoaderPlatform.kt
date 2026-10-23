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
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedlogic.platform.NightModeState
import net.bible.android.view.activity.page.screen.readingBackgroundArgb
import net.bible.sharedui.reading.session.ConfigInputs
import net.bible.sharedui.reading.session.DocumentLoaderPlatform
import net.bible.sharedui.reading.session.ReadingOffsets
import org.koin.mp.KoinPlatform

/**
 * iOS [DocumentLoaderPlatform] for one reading window.
 *
 * Offsets come from the real layout ([ReadingOffsets], CSS px): at the top only the top safe-area inset in full screen (the
 * toolbar is an in-flow row otherwise), for the topmost pane of the split; at the bottom of the bottom pane the safe-area
 * inset plus the search sheet / agent log (Android's `BibleView.topOffset` / `bottomOffset`; a pane that is not at the
 * top / bottom of the split gets 0). [lastUpdated] stays 0, so every load starts at the page manager's current location.
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
    private val offsets: () -> ReadingOffsets = { ReadingOffsets() },
    private val isPortrait: () -> Boolean = ::iosIsPortrait,
    /** Android's `CommonUtils.isBeta`: the error box is a beta-only debug aid, so the setting counts only in a beta build. */
    private val isBeta: () -> Boolean = { KoinPlatform.getKoin().get<AppEnvironment>().isBeta },
    /**
     * Told the window's page background (ARGB) every time a `set_config` is built, so the native WebView background
     * follows night, colour scheme and monochrome like Android's `BibleView.updateBackgroundColor`.
     */
    private val onPageBackground: (Int) -> Unit = {},
) : DocumentLoaderPlatform {
    override val lastUpdated: Long get() = 0L
    override val topOffset: Float get() = if (isTopWindow) offsets().top else 0f
    override val bottomOffset: Float get() = if (isBottomWindow) offsets().bottom else 0f

    /** The mirror of [isBottomWindow]: every pane counts as top when the split is side by side. */
    private val isTopWindow: Boolean
        get() {
            val reverse = repository.workspaceSettings.enableReverseSplitMode
            val splitVertically = if (reverse) !isPortrait() else isPortrait()
            return !splitVertically || repository.firstVisibleWindow == window()
        }

    /** Android's `BibleView.isBottomWindow`: every pane counts as bottom when the split is side by side. */
    private val isBottomWindow: Boolean
        get() {
            val reverse = repository.workspaceSettings.enableReverseSplitMode
            val splitVertically = if (reverse) !isPortrait() else isPortrait()
            return !splitVertically || repository.lastVisibleWindow == window()
        }

    private val repository get() = windowControl().windowRepository

    private val isActive get() = windowControl().activeWindow.id == window().id

    override val hasActiveIndicator: Boolean
        get() = settings().getBoolean("show_active_window_indicator", true) && isActive && repository.visibleWindows.size > 1

    override fun configInputs(favouriteLabelIds: List<IdType>): ConfigInputs {
        val s = settings()
        val w = window()
        val workspaceSettings = repository.workspaceSettings
        val colorMode = DisplayColorMode.fromValue(s.getString("display_color_mode", null)) ?: DisplayColorMode.NORMAL
        val displaySettings = w.pageManager.actualTextDisplaySettings
        val night = nightModeState().nightMode
        onPageBackground(readingBackgroundArgb(displaySettings.colors, night, colorMode != DisplayColorMode.NORMAL))
        return ConfigInputs(
            displaySettings = displaySettings,
            isActive = isActive,
            isBottomWindow = repository.lastVisibleWindow == w,
            hasActiveIndicator = hasActiveIndicator,
            nightMode = night,
            showErrorBox = isBeta() && s.getBoolean("show_errorbox", false),
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
