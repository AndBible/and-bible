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
package net.bible.sharedcore.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two first-run views. EASY exists only where Easy start is available (English). */
enum class StartupWelcomeTab { EASY, ADVANCED }

/** Host-supplied primitives for the first-run welcome screen. Static text comes from `Strings`. */
data class StartupWelcomeInfo(
    val versionText: String,
    val supportedFormatsText: String,
    val previousInstallDetected: Boolean,
    val easyStartAvailable: Boolean,
    /** Batch 6 A6: false in discrete mode -- the Homepage / GitHub buttons open AndBible URLs. */
    val homepageButtonsVisible: Boolean = true,
)

/** View-data the composable renders. */
data class StartupWelcomeState(
    val versionText: String,
    val supportedFormatsText: String,
    /** The Easy | Advanced switch; only where Easy start exists. Without it the screen is the Advanced view. */
    val showTabs: Boolean,
    val selectedTab: StartupWelcomeTab,
    /** The Advanced "Redownload" row. */
    val showRedownload: Boolean,
    /** The Easy view's "Previous documents found" hint. */
    val showRedownloadHint: Boolean,
    val progressText: String? = null,
    /** Batch 6 A6: false in discrete mode -- hides the Homepage and GitHub buttons (they name AndBible). */
    val homepageButtonsVisible: Boolean = true,
)

/**
 * Framework-free state holder for the first-run welcome screen. Easy start (and so the Easy tab) is
 * English-only because only English has curated default documents; elsewhere the screen is the
 * Advanced list alone. Restore is always offered; Redownload only after a previous install.
 * [setProgress] carries the `InstallZipEvent` line. Button ACTIONS are host seams passed to the
 * screen, not this controller. No Android/JSword/Intent types.
 */
class StartupWelcomeController(
    private val loadInfo: () -> StartupWelcomeInfo,
) {
    private val _state = MutableStateFlow(toState(loadInfo(), progress = null, tab = StartupWelcomeTab.EASY))
    val state: StateFlow<StartupWelcomeState> = _state.asStateFlow()

    /** Recompute from the latest host info (e.g. after a download/restore flow), preserving progress and tab. */
    fun refresh() {
        val current = _state.value
        _state.value = toState(loadInfo(), current.progressText, current.selectedTab)
    }

    fun setProgress(text: String?) { _state.value = _state.value.copy(progressText = text) }

    /** Ignored when there are no tabs: the screen is then the Advanced view only. */
    fun selectTab(tab: StartupWelcomeTab) {
        val current = _state.value
        if (current.showTabs) _state.value = current.copy(selectedTab = tab)
    }

    private fun toState(info: StartupWelcomeInfo, progress: String?, tab: StartupWelcomeTab): StartupWelcomeState {
        val tabs = info.easyStartAvailable
        return StartupWelcomeState(
            versionText = info.versionText,
            supportedFormatsText = info.supportedFormatsText,
            showTabs = tabs,
            selectedTab = if (tabs) tab else StartupWelcomeTab.ADVANCED,
            showRedownload = info.previousInstallDetected,
            showRedownloadHint = tabs && info.previousInstallDetected,
            progressText = progress,
            homepageButtonsVisible = info.homepageButtonsVisible,
        )
    }
}
