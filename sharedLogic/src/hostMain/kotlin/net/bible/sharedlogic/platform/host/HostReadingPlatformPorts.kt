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

package net.bible.sharedlogic.platform.host

import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowSettingsPicker
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryPlatform
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult

/**
 * iOS [KeyChooserLauncher]: [start] is the reading navigator's key-chooser routing (the same routes as Android's
 * `AndroidKeyChooserLauncher`); the shared root supplies it once the reading host exists. Answers whether a chooser was
 * started: false while no reading host exists yet.
 */
class HostKeyChooserLauncher(private val start: (CurrentPage) -> Boolean) : KeyChooserLauncher {
    override fun startKeyChooser(page: CurrentPage): Boolean = start(page)
}

/**
 * iOS [LinkPlatform], the activity half of the link handling over the shared root: [openRoute] navigates the single
 * NavHost (Android starts the nav-host activity on the route), [searchStrongs] is the reading view's in-place find-all
 * (false: not handled, the caller falls back to its own route), [reportError] has no iOS error-report flow yet.
 */
class HostLinkPlatform(
    private val openRoute: (String) -> Unit,
    private val searchStrongs: (ref: String, documentInitials: List<String>) -> Boolean,
    private val reportError: () -> Unit,
) : LinkPlatform {
    override fun searchStrongsInReadingView(ref: String, documentInitials: List<String>): Boolean = searchStrongs(ref, documentInitials)

    override fun openRoute(route: String) = openRoute.invoke(route)

    override fun reportJsError() = reportError()
}

/**
 * iOS [HistoryPlatform]. [isOnReading] is the same predicate Android's `ReadingViewVisibility` answers (the reading
 * destination is the current one), [leave] pops the screen on top (Android: `leaveCurrentScreen` of the activity).
 * There is no screen-bound history item on iOS: the single NavHost's back stack is the screen history, so
 * [screenHistoryItem] is null and the history list only holds verse entries.
 */
class HostHistoryPlatform(
    private val isOnReading: () -> Boolean = { true },
    private val leave: () -> Unit = {},
) : HistoryPlatform {
    override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
    override fun isOnReadingScreen(): Boolean = isOnReading()
    override fun leaveCurrentScreen() = leave()
}

/**
 * iOS [WindowSettingsPicker]: the copy-settings multi-select as Android's `AndroidWindowSettingsPicker` (a
 * `MultiChoice` of the setting titles, no row checked at the start); Cancel and "OK with nothing checked" both come back
 * as an empty list. Rows are [title] per type (the real prefs items' titles, so dynamic values like the font size show);
 * texts are string resource keys through [text] (`copy_settings_title`, `okay`, `cancel`, `select_all`, `select_none`).
 */
class HostWindowSettingsPicker(
    private val dialogs: AppDialogController,
    private val title: (Window, TextDisplaySettings.Types) -> String,
    private val text: (String) -> String,
) : WindowSettingsPicker {
    override suspend fun chooseSettingsToCopy(window: Window, types: List<TextDisplaySettings.Types>): List<TextDisplaySettings.Types> =
        ask(types) { title(window, it) }

    /** The dialog and the mapping of its answer; [labelOf] is the row label (the window-free body, testable). */
    internal suspend fun ask(
        types: List<TextDisplaySettings.Types>,
        labelOf: (TextDisplaySettings.Types) -> String,
    ): List<TextDisplaySettings.Types> {
        val result = dialogs.await(
            AppDialogRequest.MultiChoice(
                title = text("copy_settings_title"),
                options = types.mapIndexed { index, type -> SettingsItem.Choice(index.toString(), labelOf(type)) },
                selectedIds = emptyList(),
                confirmText = text("okay"),
                dismissText = text("cancel"),
                selectAllText = text("select_all"),
                selectNoneText = text("select_none"),
            ),
        )
        val ids = (result as? AppDialogResult.SelectedMany)?.ids ?: return emptyList()
        return types.filterIndexed { index, _ -> index.toString() in ids }
    }
}
