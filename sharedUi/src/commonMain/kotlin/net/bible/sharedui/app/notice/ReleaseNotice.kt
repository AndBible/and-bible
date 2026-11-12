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

package net.bible.sharedui.app.notice

import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.ReleaseNoticeAction

/** The platform showing the 6.0 release notice; iOS has no sponsor action. */
@kotlin.native.HiddenFromObjC
enum class ReleaseNoticePlatform { ANDROID, IOS, DESKTOP }

/**
 * The 6.0 stable release notice. The gate is shared by every platform; the host supplies
 * [showIfNeeded]'s `ask` (dialog) and `onAction` (what each button does).
 *
 * The checkbox remembers a main version (e.g. "6.0") under [DISPLAYED_KEY]: patches stay quiet, the next minor
 * shows again. Completing Welcome silently marks that version instead. Beta and discrete builds never read or write it.
 */
@kotlin.native.HiddenFromObjC
object ReleaseNotice {
    /**
     * The settings key holding the main version whose notice was last seen. Not the old Android key
     * `stable-notice-displayed`: the internal 6.0.1266 test build's earlier gate silently stored "6.0" there on an
     * undetected beta -> stable update, which would hide the notice for good.
     */
    const val DISPLAYED_KEY = "release-notice-displayed"

    /** Call on successful Welcome completion, before reading can check notices; showing Welcome alone is not enough. */
    fun dismissForWelcome(isBeta: Boolean, discrete: Boolean, mainVersion: String, settings: AppSettings) {
        if (!isBeta && !discrete) settings.setString(DISPLAYED_KEY, mainVersion)
    }

    /** Whether to ask: a stable, non-discrete build and [mainVersion] not yet marked [displayed]. */
    fun shouldShow(isBeta: Boolean, discrete: Boolean, mainVersion: String, displayed: String): Boolean =
        !isBeta && !discrete && displayed != mainVersion

    /**
     * Returns true only when the user's "don't show again" stored the main version. Closing without the checkbox
     * leaves the notice enabled for the next launch. A chosen action runs after the dialog closes.
     */
    suspend fun showIfNeeded(
        platform: ReleaseNoticePlatform,
        isBeta: Boolean,
        discrete: Boolean,
        mainVersion: String,
        settings: AppSettings,
        ask: suspend (AppDialogRequest) -> AppDialogResult,
        onAction: suspend (ReleaseNoticeAction) -> Unit,
    ): Boolean {
        if (isBeta || discrete) return false
        val displayed = settings.getString(DISPLAYED_KEY, "") ?: ""
        if (!shouldShow(isBeta, discrete, mainVersion, displayed)) return false
        val answer = ask(AppDialogRequest.ReleaseNotice(
            mainVersion, sponsorAllowed = platform != ReleaseNoticePlatform.IOS,
            computerAllowed = platform != ReleaseNoticePlatform.DESKTOP,
        ))
            as? AppDialogResult.ReleaseNoticeAnswer ?: return false
        val stored = answer.dontShowAgain
        if (stored) settings.setString(DISPLAYED_KEY, mainVersion)
        answer.action?.let { onAction(it) }
        return stored
    }
}
