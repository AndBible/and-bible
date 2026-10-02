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

/** Host-supplied primitives for the first-run welcome screen (all text pre-composed by the host). */
data class StartupWelcomeInfo(
    val welcomeText: String,
    val versionText: String,
    val supportedFormatsText: String,
    val redownloadMessage: String,
    val easyStartMessage: String,
    val previousInstallDetected: Boolean,
    val easyStartAvailable: Boolean,
    /** Batch 6 A6: false in discrete mode -- the Homepage / GitHub buttons open AndBible URLs. */
    val homepageButtonsVisible: Boolean = true,
)

/** View-data the composable renders. */
data class StartupWelcomeState(
    val welcomeText: String,
    val versionText: String,
    val supportedFormatsText: String,
    val redownloadMessage: String,
    val easyStartMessage: String,
    val showRedownload: Boolean,
    val showRestore: Boolean,
    val showEasyStart: Boolean,
    val progressText: String? = null,
    /** Batch 6 A6: false in discrete mode -- hides the Homepage and GitHub buttons (they name AndBible). */
    val homepageButtonsVisible: Boolean = true,
)

/**
 * Framework-free state holder for the first-run welcome screen. Visibility rules mirror classic
 * `StartupActivity.showFirstLayout()`: a previous install → Redownload (Restore hidden), otherwise
 * → Restore; Easy start only when the host reports it available (locale == "en"). [setProgress]
 * carries the `InstallZipEvent` line. All button ACTIONS are host seams passed to the screen, not
 * this controller — it only derives presentation. No Android/JSword/Intent types.
 */
class StartupWelcomeController(
    private val loadInfo: () -> StartupWelcomeInfo,
) {
    private val _state = MutableStateFlow(toState(loadInfo(), null))
    val state: StateFlow<StartupWelcomeState> = _state.asStateFlow()

    /** Recompute from the latest host info (e.g. after a download/restore flow), preserving progress. */
    fun refresh() { _state.value = toState(loadInfo(), _state.value.progressText) }

    fun setProgress(text: String?) { _state.value = _state.value.copy(progressText = text) }

    private fun toState(info: StartupWelcomeInfo, progress: String?) = StartupWelcomeState(
        welcomeText = info.welcomeText,
        versionText = info.versionText,
        supportedFormatsText = info.supportedFormatsText,
        redownloadMessage = info.redownloadMessage,
        easyStartMessage = info.easyStartMessage,
        showRedownload = info.previousInstallDetected,
        showRestore = !info.previousInstallDetected,
        showEasyStart = info.easyStartAvailable,
        progressText = progress,
        homepageButtonsVisible = info.homepageButtonsVisible,
    )
}
