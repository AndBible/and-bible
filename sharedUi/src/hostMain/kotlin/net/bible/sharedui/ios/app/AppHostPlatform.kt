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

package net.bible.sharedui.ios.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppResultChannels
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.HostLifecycle
import net.bible.sharedui.ios.reading.DisplayModeSignals
import net.bible.sharedui.ios.reading.ReadingHostPlatform
import net.bible.sharedui.ios.reading.ReadingPaneEngine
import net.bible.sharedui.ios.reading.TiltScrollSession
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import org.koin.core.Koin

/**
 * The platform hooks of the app root ([IosApp]): everything it needs that is not shared code. iOS passes
 * `IosAppHostPlatform` (the UIKit calls and iOS objects the root used directly before). The debug/UI-test members
 * (`Debug*`, [receiveIncomingFiles]'s harness preflight, [debugArgs]) mean "nothing" on a host without them.
 *
 * Composable members are called at the same place in the tree as the code they replace; non-composable members are
 * called at the moment that code ran. Main thread only, like the composition.
 */
@kotlin.native.HiddenFromObjC
interface AppHostPlatform {
    /** Debug launch arguments (iOS: `IosDebugLaunchArgs`). */
    val debugArgs: HostDebugArgs

    /** Active / resign-active / background notifications (iOS: `IosHostLifecycle`). */
    val lifecycle: HostLifecycle

    /** The reading host's platform calls (iOS: `IosReadingHostPlatform`). */
    val readingHost: ReadingHostPlatform

    /** The reading windows' web engine (iOS: `IosReadingPaneEngine`). */
    val paneEngine: ReadingPaneEngine

    /** The night-mode observer's display-mode signals (iOS: `IosDisplayModeSignals`). */
    val displayModeSignals: DisplayModeSignals

    /** The app dialog overlay's host (iOS: `IosAppDialogHost`). */
    val dialogHost: AppDialogHost

    /** The UI language a "follow the device" language switch resolves to, from the device prefs in [koin]. */
    fun deviceUiLanguage(koin: Koin): String

    /** Runs the bug-report flow (confirmation, log attachment, mail) in [table]'s language; [cause] is the error, if any. */
    suspend fun reportBug(table: StringTable, cause: Throwable?)

    /** The app's [HostPorts] over [shell]; called once per app content composition. */
    fun buildHostPorts(
        shell: HostShell,
        koin: Koin,
        table: StringTable,
        initialiseWelcome: suspend () -> Unit,
        isPortrait: () -> Boolean,
    ): HostPorts

    /** The reading view's pane factory over `IosReadingWindows`; called once per reading destination. */
    fun readingPaneFactory(): ReadingPaneFactory

    /** Keeps the display awake while [disabled] is true (iOS: `UIApplication.idleTimerDisabled`). */
    fun setIdleTimerDisabled(disabled: Boolean)

    /** A new tilt-to-scroll session for the reading view (one per reading destination composition). */
    fun newTiltScroll(koin: Koin): TiltScrollSession

    /**
     * Asks for background time for the workspace save; [onExpire] runs if it runs out. Returns the task id, or
     * [invalidBackgroundTaskId] when none was granted. A host without background tasks always returns that id.
     */
    fun beginBackgroundTask(onExpire: () -> Unit): ULong

    /** Ends the task [id] that [beginBackgroundTask] returned. */
    fun endBackgroundTask(id: ULong)

    /** The id of "no background task"; it is never ended. */
    val invalidBackgroundTaskId: ULong

    /** Wraps the app content with the host's text scale (iOS: Dynamic Type); a host without one shows [content] as is. */
    @Composable
    fun TextScale(content: @Composable () -> Unit)

    /** Debug-only font-scale evidence for UI tests; nothing on other hosts. */
    @Composable
    fun DebugFontScaleProbe()

    /** Debug-only UI-test harness overlays (Files and incoming-file harnesses); nothing on other hosts. */
    @Composable
    fun DebugHarnessOverlays(strings: Strings, navController: NavHostController, navigate: (String) -> Unit)

    /** Debug-only feature routes for UI tests; [askBookmarks] records the Bookmarks chooser as the drawer row does. */
    @Composable
    fun DebugFeatureRoutes(
        ready: Boolean,
        nav: NavHostController,
        channels: AppResultChannels,
        koin: Koin,
        hosts: AppHosts,
        askBookmarks: () -> Unit,
    )

    /**
     * Delivers files handed to the app from outside (share / "open in") to [open] until cancelled; [open]'s second
     * argument acknowledges the batch, [onFailure] reports a failed copy. Returns at once on a host without them.
     */
    suspend fun receiveIncomingFiles(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit)
}
