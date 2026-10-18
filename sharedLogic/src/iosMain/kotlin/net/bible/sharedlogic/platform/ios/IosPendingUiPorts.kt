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

package net.bible.sharedlogic.platform.ios

import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowSettingsPicker
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryPlatform
import net.bible.sharedcore.log.Log

/** Log tag of every C7 pending port. */
internal const val C7_PENDING_TAG = "C7-pending"

private fun pending(what: String) { Log.w(C7_PENDING_TAG, "$what (phase 2: route through the shared root)") }

/**
 * C7 pending: no key chooser screen on iOS yet. Phase 2: route through the shared root. Logs once per call and
 * answers false (not started).
 */
class PendingKeyChooserLauncher : KeyChooserLauncher {
    override fun startKeyChooser(page: CurrentPage): Boolean = notStarted()

    /** The argument-free body (a `CurrentPage` is not constructible in a platform test); logs once per call. */
    internal fun notStarted(): Boolean {
        pending("PendingKeyChooserLauncher.startKeyChooser: not started")
        return false
    }
}

/**
 * C7 pending: no "copy settings from window" picker on iOS yet. Phase 2: route through the shared root. Logs once per
 * call and answers the cancelled result, an empty list.
 */
class PendingWindowSettingsPicker : WindowSettingsPicker {
    override suspend fun chooseSettingsToCopy(window: Window, types: List<TextDisplaySettings.Types>): List<TextDisplaySettings.Types> =
        cancelled()

    /** The argument-free body (a `Window` is not constructible in a platform test); logs once per call. */
    internal fun cancelled(): List<TextDisplaySettings.Types> {
        pending("PendingWindowSettingsPicker.chooseSettingsToCopy: cancelled")
        return emptyList()
    }
}

/**
 * C7 pending: the activity half of the link handling (open a route, search Strong's in the reading view, report a
 * JS error) is not wired on iOS yet. Phase 2: route through the shared root. Each call logs once;
 * [searchStrongsInReadingView] answers false (not handled).
 */
class PendingLinkPlatform : LinkPlatform {
    override fun searchStrongsInReadingView(ref: String, documentInitials: List<String>): Boolean {
        pending("PendingLinkPlatform.searchStrongsInReadingView($ref): not handled")
        return false
    }

    override fun openRoute(route: String) = pending("PendingLinkPlatform.openRoute($route): ignored")

    override fun reportJsError() = pending("PendingLinkPlatform.reportJsError: ignored")
}

/**
 * iOS [HistoryPlatform] while the app has a single reading screen: no screen-bound history item, the reading screen is
 * always the current one, and there is no other screen to leave. Not a C7 pending port; a navigation host (phase 2) may
 * replace it.
 */
class IosHistoryPlatform : HistoryPlatform {
    override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
    override fun isOnReadingScreen(): Boolean = true
    override fun leaveCurrentScreen() = Unit
}
