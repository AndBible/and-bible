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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.WindowControl
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.ios.IosPending
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.host.ReadingHostDeps
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.webview.PendingIosReadingNavigator
import org.koin.core.Koin

/**
 * The iOS reading host's dependencies: the common [ReadingHostState] and the [ReadingHostDeps] / [ReadingNavDeps]
 * built from Koin. Construct only after [net.bible.service.startup.ReadingAppInit.createWindowRepository] has run,
 * because [hostState] reads the [WindowControl]'s repository. Slots stay at their defaults; Stream R lifts them.
 */
@kotlin.native.HiddenFromObjC
class IosReadingHost(
    private val scope: CoroutineScope,
    private val shell: IosHostShell,
    private val koin: Koin,
    private val nightModeState: NightModeState = koin.get(),
    /** A live night-mode source (see [IosNightModeObserver]); null keeps a snapshot of [nightModeState]. */
    private val nightModeSource: State<Boolean>? = null,
) {
    private val windowControl: WindowControl = koin.get()

    val hostState: ReadingHostState = ReadingHostState(
        scope = scope,
        settings = koin.get<AppSettings>(),
        windowControl = windowControl,
        historyManager = koin.get<HistoryManager>(),
        documentControl = koin.get<DocumentControl>(),
    )

    private fun callbacks(): ReadingToolbarCallbacks {
        val navigator = PendingIosReadingNavigator
        return ReadingToolbarCallbacks(
            onHome = { IosPending.log("ReadingToolbar.onHome") },
            onTitleTap = {
                // A tap before the repository holds a window must not throw.
                val page = try {
                    windowControl.activeWindowPageManager.currentPage
                } catch (e: Exception) {
                    null
                }
                if (page == null) IosPending.log("ReadingToolbar.onTitleTap") else navigator.startKeyChooser(page)
            },
            onTitleLongPress = { IosPending.log("ReadingToolbar.onTitleLongPress") },
            onTitleFlingVertical = { navigator.openWorkspaceSelector() },
            onTitleFlingHorizontal = { IosPending.log("ReadingToolbar.onTitleFlingHorizontal") },
            onBible = { IosPending.log("ReadingToolbar.onBible") },
            onBibleLong = { IosPending.log("ReadingToolbar.onBibleLong") },
            onCommentary = { IosPending.log("ReadingToolbar.onCommentary") },
            onCommentaryLong = { IosPending.log("ReadingToolbar.onCommentaryLong") },
            onStrongs = { IosPending.log("ReadingToolbar.onStrongs") },
            onStrongsLong = { IosPending.log("ReadingToolbar.onStrongsLong") },
            onSearch = { navigator.openSearch(null) },
            onSpeak = { IosPending.log("ReadingToolbar.onSpeak") },
            onSpeakLong = { IosPending.log("ReadingToolbar.onSpeakLong") },
            onWorkspace = { navigator.openWorkspaceSelector() },
            onOverflow = { IosPending.log("ReadingToolbar.onOverflow") },
        )
    }

    fun deps(icons: ReadingToolbarIcons): ReadingHostDeps = ReadingHostDeps(
        state = hostState,
        controller = ReadingViewController(koin.get<WindowStateService>(), koin.get<WindowCommands>()),
        toolbarCallbacks = callbacks(),
        toolbarIcons = { icons },
        nightMode = nightModeSource ?: mutableStateOf(nightModeState.nightMode),
    )

    fun navDeps(content: @Composable () -> Unit): ReadingNavDeps = iosReadingNavDeps(this, shell, IosAppStartup.stringTable, content)
}

/** The reading destination's [ReadingNavDeps]; [hostToken] is the identity the destination registers under. */
@kotlin.native.HiddenFromObjC
fun iosReadingNavDeps(
    hostToken: Any,
    shell: IosHostShell,
    table: net.bible.sharedui.strings.keyed.StringTable,
    content: @Composable () -> Unit,
): ReadingNavDeps = ReadingNavDeps(
    host = hostToken,
    windowTitle = table.text("app_name_short"),
    onBack = { passThrough -> passThrough() },
    content = content,
    onKey = { false },
    onScreenTurnedOn = {},
    onScreenTurnedOff = {},
    setWindowTitle = shell::setWindowTitle,
    results = emptyList(),
)
