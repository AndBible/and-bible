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
enum class ReleaseNoticePlatform { ANDROID, IOS }

/**
 * The one-time 6.0 stable release notice for upgraders. The gate is shared by every platform; the host supplies
 * [showIfNeeded]'s `ask` (dialog) and `onAction` (what each button does).
 *
 * The notice is remembered per main version (e.g. "6.0") under [DISPLAYED_KEY]: a patch update does not show it
 * again, the next minor does. A fresh stable install does not see it, but an earlier beta run counts as an upgrade.
 */
@kotlin.native.HiddenFromObjC
object ReleaseNotice {
    /** The settings key holding the main version whose notice was last seen (the name is the old Android key). */
    const val DISPLAYED_KEY = "stable-notice-displayed"

    /** The main version last run as a non-discrete beta. */
    const val BETA_RAN_KEY = "stable-notice-beta-ran"

    /** Whether to ask: a stable, non-discrete build, an upgrade, and [mainVersion] not yet marked [displayed]. */
    fun shouldShow(isBeta: Boolean, discrete: Boolean, isUpgrade: Boolean, mainVersion: String, displayed: String): Boolean =
        !isBeta && !discrete && isUpgrade && displayed != mainVersion

    /**
     * A non-discrete beta records [mainVersion] separately without touching [DISPLAYED_KEY]. A stable run of that
     * version is treated as an upgrade, even if [isUpgrade] is false. Other fresh installs silently store the version,
     * including discrete installs, so later patches stay quiet. Discrete builds never show the notice.
     * Returns true only when the user's "don't show again" stored it. A chosen action runs after the dialog closes.
     */
    suspend fun showIfNeeded(
        platform: ReleaseNoticePlatform,
        isBeta: Boolean,
        discrete: Boolean,
        isUpgrade: Boolean,
        mainVersion: String,
        settings: AppSettings,
        ask: suspend (AppDialogRequest) -> AppDialogResult,
        onAction: suspend (ReleaseNoticeAction) -> Unit,
    ): Boolean {
        if (isBeta) {
            if (!discrete) settings.setString(BETA_RAN_KEY, mainVersion)
            return false
        }
        val displayed = settings.getString(DISPLAYED_KEY, "") ?: ""
        val effectiveUpgrade = isUpgrade || settings.getString(BETA_RAN_KEY, "") == mainVersion
        if (!effectiveUpgrade && displayed.isEmpty()) {
            settings.setString(DISPLAYED_KEY, mainVersion)
            return false
        }
        if (!shouldShow(isBeta, discrete, effectiveUpgrade, mainVersion, displayed)) return false
        val answer = ask(AppDialogRequest.ReleaseNotice(mainVersion, sponsorAllowed = platform == ReleaseNoticePlatform.ANDROID))
            as? AppDialogResult.ReleaseNoticeAnswer ?: return false
        val stored = answer.dontShowAgain
        if (stored) settings.setString(DISPLAYED_KEY, mainVersion)
        answer.action?.let { onAction(it) }
        return stored
    }
}
