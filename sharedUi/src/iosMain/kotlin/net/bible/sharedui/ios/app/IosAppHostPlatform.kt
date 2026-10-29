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
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package net.bible.sharedui.ios.app

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppResultChannels
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.HostLifecycle
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.assembly.IosHostLifecycle
import net.bible.sharedui.assembly.iosPreferredLanguages
import net.bible.sharedui.assembly.resolveDeviceUiLanguage
import net.bible.sharedui.components.LocalBarFontScaleCap
import net.bible.sharedui.ios.files.IosFilesFeatureHarness
import net.bible.sharedui.ios.files.IosFilesRuntimeHarness
import net.bible.sharedui.ios.files.IosIncomingFiles
import net.bible.sharedui.ios.files.IosIncomingRuntimeHarness
import net.bible.sharedui.host.reading.DisplayModeSignals
import net.bible.sharedui.ios.reading.IosDisplayModeSignals
import net.bible.sharedui.ios.reading.IosReadingHostPlatform
import net.bible.sharedui.host.reading.HostReadingWindows
import net.bible.sharedui.ios.reading.IosTiltScroll
import net.bible.sharedui.host.reading.ReadingHostPlatform
import net.bible.sharedui.host.reading.ReadingPaneEngine
import net.bible.sharedui.host.reading.TiltScrollSession
import net.bible.sharedui.ios.report.IosBugReport
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.webview.IosReadingPaneEngine
import net.bible.sharedui.webview.iosReadingPaneFactory
import org.koin.core.Koin
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIBackgroundTaskInvalid
import net.bible.sharedui.host.app.AppDialogHost
import net.bible.sharedui.host.app.AppHostPlatform

/**
 * The iOS [AppHostPlatform]: exactly the objects and UIKit calls [HostApp] used directly before the seam (the same
 * singletons, a new instance where the root built one per use). Passed by `MainViewController`.
 */
@kotlin.native.HiddenFromObjC
object IosAppHostPlatform : AppHostPlatform {
    override val debugArgs: HostDebugArgs get() = IosDebugLaunchArgs
    override val lifecycle: HostLifecycle get() = IosHostLifecycle
    override val readingHost: ReadingHostPlatform get() = IosReadingHostPlatform
    override val paneEngine: ReadingPaneEngine get() = IosReadingPaneEngine
    override val displayModeSignals: DisplayModeSignals get() = IosDisplayModeSignals
    override val dialogHost: AppDialogHost get() = IosAppDialogHost

    override fun deviceUiLanguage(koin: Koin): String =
        resolveDeviceUiLanguage(koin.get<IosDeviceSettings>(), ::iosPreferredLanguages)

    override suspend fun reportBug(table: StringTable, cause: Throwable?) {
        IosBugReport(table).report(cause)
    }

    override fun buildHostPorts(
        shell: HostShell,
        koin: Koin,
        table: StringTable,
        initialiseWelcome: suspend () -> Unit,
        isPortrait: () -> Boolean,
    ): HostPorts = buildIosHostPorts(shell, koin, table, initialiseWelcome = initialiseWelcome, isPortrait = isPortrait)

    override fun readingPaneFactory(): ReadingPaneFactory =
        iosReadingPaneFactory(HostReadingWindows::sessionFor, HostReadingWindows::bridgeFor, { HostReadingWindows.nightMode }, HostReadingWindows::isOpen)

    override fun setIdleTimerDisabled(disabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = disabled
    }

    override fun newTiltScroll(koin: Koin): TiltScrollSession = IosTiltScroll(koin)

    override fun beginBackgroundTask(onExpire: () -> Unit): ULong =
        UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler(onExpire)

    override fun endBackgroundTask(id: ULong) {
        UIApplication.sharedApplication.endBackgroundTask(id)
    }

    override val invalidBackgroundTaskId: ULong get() = UIBackgroundTaskInvalid

    @Composable
    override fun TextScale(content: @Composable () -> Unit) = IosTextScale(content)

    @Composable
    override fun DebugFontScaleProbe() = IosDebugFontScaleProbe()

    @Composable
    override fun DebugHarnessOverlays(strings: Strings, navController: NavHostController, navigate: (String) -> Unit) {
        IosFilesRuntimeHarness.Overlay(strings) {
            navController.currentBackStackEntryFlow.first()
        }
        val incomingEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
        IosIncomingRuntimeHarness.Overlay(
            strings,
            backupRoute = incomingEntry?.destination?.route == NavRoutes.BACKUP,
            openBackup = { navigate(NavRoutes.BACKUP) },
        )
        IosFilesFeatureHarness.Overlay(
            strings, incomingEntry?.destination?.route,
            awaitGraph = { navController.currentBackStackEntryFlow.first() },
            navigate = { navigate(it) },
        )
    }

    @Composable
    override fun DebugFeatureRoutes(
        ready: Boolean,
        nav: NavHostController,
        channels: AppResultChannels,
        koin: Koin,
        hosts: AppHosts,
        askBookmarks: () -> Unit,
    ) = IosDebugFeatureRoutes(ready, nav, channels, koin, hosts, askBookmarks)

    override suspend fun receiveIncomingFiles(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit) {
        if (!IosIncomingRuntimeHarness.preflightBeforeBind()) return
        val owner = IosIncomingFiles.bind(open = open, onFailure = onFailure)
        try { kotlinx.coroutines.awaitCancellation() }
        finally { IosIncomingFiles.detach(owner) }
    }
}

/** Replace Compose's stale launch scale absolutely, never multiply its built-in category mapping. */
@Composable
private fun IosTextScale(content: @Composable () -> Unit) {
    val observer = remember { IosDynamicTypeObserver() }
    DisposableEffect(observer) {
        observer.start()
        onDispose { observer.stop() }
    }
    val scale by observer.fontScale
    val current = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(current.density, fontScale = scale),
        LocalBarFontScaleCap provides IOS_BAR_FONT_SCALE_CAP,
        content = content,
    )
}

/** Reports effective Compose density and UIKit delivery evidence while the real app resolves its start route. */
@Composable
private fun IosDebugFontScaleProbe() {
    if (!kotlin.native.Platform.isDebugBinary) return
    val enabled = remember { "-debugFontScaleProbe" in platform.Foundation.NSProcessInfo.processInfo.arguments }
    if (!enabled) return
    var systemSnapshot by remember { mutableStateOf(iosDebugTextSizeSnapshot("initial", 0)) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        var notifications = 0
        val sizeObserver = center.addObserverForName(
            platform.UIKit.UIContentSizeCategoryDidChangeNotification, null, NSOperationQueue.mainQueue,
        ) {
            notifications++
            systemSnapshot = iosDebugTextSizeSnapshot("content-size-notification", notifications)
        }
        val activeObserver = center.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) { systemSnapshot = iosDebugTextSizeSnapshot("became-active", notifications) }
        onDispose {
            center.removeObserver(sizeObserver)
            center.removeObserver(activeObserver)
        }
    }
    val scale = LocalDensity.current.fontScale.toString()
    Text(
        text = systemSnapshot,
        modifier = Modifier.requiredSize(1.dp).testTag("ab-font-scale-system").semantics {
            stateDescription = systemSnapshot
        },
    )
    Text(
        text = scale,
        modifier = Modifier.requiredSize(1.dp).testTag("ab-font-scale").semantics {
            stateDescription = scale
        },
    )
}

/** Separate UIKit boundary evidence from Compose's ambient fontScale; never changes either one. */
private fun iosDebugTextSizeSnapshot(event: String, notifications: Int): String {
    val app = UIApplication.sharedApplication
    val appCategory = app.preferredContentSizeCategory.orEmpty()
    val windows = app.windows.filterIsInstance<platform.UIKit.UIWindow>()
    val traits = windows.joinToString(";") { window ->
        "window=${window.traitCollection.preferredContentSizeCategory},root=${window.rootViewController?.traitCollection?.preferredContentSizeCategory}"
    }
    val snapshot = "event=$event notifications=$notifications app=$appCategory $traits"
    nsLog("DEBUG font-scale UIKit: $snapshot")
    return snapshot
}
