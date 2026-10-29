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
package net.bible.sharedui.desktop.assembly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppResultChannels
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.HostLifecycle
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.desktop.webview.DesktopDisplayModeSignals
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import net.bible.sharedui.desktop.webview.DesktopReadingRecovery
import net.bible.sharedui.desktop.webview.desktopReadingPaneFactory
import net.bible.sharedui.host.app.AppDialogHost
import net.bible.sharedui.host.app.AppHostPlatform
import net.bible.sharedui.host.reading.DisplayModeSignals
import net.bible.sharedui.host.reading.IosReadingWindows
import net.bible.sharedui.host.reading.ReadingHostPlatform
import net.bible.sharedui.host.reading.ReadingPaneEngine
import net.bible.sharedui.host.reading.TiltScrollSession
import net.bible.sharedui.host.reading.ToolbarDeviceState
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import okio.Sink
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DesktopAppHostPlatform"

/** "No background task": the desktop has no background-time budget, so none is ever granted. */
private const val NO_BACKGROUND_TASK: ULong = 0UL

/**
 * The desktop [AppHostPlatform] passed to the hoisted `IosApp` root. [lifecycle] is driven by the window, [paneEngine]
 * is the reading windows' KCEF engine (the same instance `IosApp` binds into `IosReadingWindows` and the pane factory
 * reads), [window] the application window. Everything Koin-backed resolves from the global Koin on use, so the platform
 * can be built before the startup has finished.
 *
 * Desktop answers: no debug arguments, probes, harness overlays or feature routes; no background tasks (the exit path
 * joins the workspace save itself, see `DesktopAppStartup.persistBeforeExit`); no idle timer; no tilt sensor; the
 * text scale as Compose has it; no incoming files until D3b.
 */
class DesktopAppHostPlatform(
    override val lifecycle: DesktopHostLifecycle,
    override val paneEngine: DesktopReadingPaneEngine,
    private val window: DesktopWindowHandle,
    private val koin: () -> Koin = { KoinPlatform.getKoin() },
) : AppHostPlatform {
    override val debugArgs: HostDebugArgs get() = DesktopNoDebugArgs
    override val readingHost: ReadingHostPlatform by lazy { DesktopReadingHostPlatform(window, koin) }
    override val displayModeSignals: DisplayModeSignals by lazy { DesktopDisplayModeSignals(koin().get<DesktopDeviceSettings>()) }
    override val dialogHost: AppDialogHost by lazy { DesktopAppDialogHost(koin) }

    override fun deviceUiLanguage(koin: Koin): String = DesktopUiLanguage.resolve(koin.get<DesktopDeviceSettings>())

    override suspend fun reportBug(table: StringTable, cause: Throwable?) {
        koin().get<DesktopBugReport>().report(cause)
    }

    override fun buildHostPorts(
        shell: HostShell,
        koin: Koin,
        table: StringTable,
        initialiseWelcome: suspend () -> Unit,
        isPortrait: () -> Boolean,
    ): HostPorts = buildDesktopHostPorts(koin, shell, table, initialiseWelcome, isPortrait)

    override fun readingPaneFactory(): ReadingPaneFactory = desktopReadingPaneFactory(
        paneEngine, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor, { IosReadingWindows.nightMode }, IosReadingWindows::isOpen,
    )

    override fun setIdleTimerDisabled(disabled: Boolean) = Unit

    override fun newTiltScroll(koin: Koin): TiltScrollSession = DesktopNoTiltScroll

    override fun beginBackgroundTask(onExpire: () -> Unit): ULong = NO_BACKGROUND_TASK

    override fun endBackgroundTask(id: ULong) = Unit

    override val invalidBackgroundTaskId: ULong get() = NO_BACKGROUND_TASK

    @Composable
    override fun TextScale(content: @Composable () -> Unit) = content()

    @Composable
    override fun DebugFontScaleProbe() = Unit

    @Composable
    override fun DebugHarnessOverlays(strings: Strings, navController: NavHostController, navigate: (String) -> Unit) = Unit

    @Composable
    override fun DebugFeatureRoutes(
        ready: Boolean,
        nav: NavHostController,
        channels: AppResultChannels,
        koin: Koin,
        hosts: AppHosts,
        askBookmarks: () -> Unit,
    ) = Unit

    /** No share / "open with" delivery on the desktop yet (D3b): suspends until cancelled. */
    override suspend fun receiveIncomingFiles(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit) {
        awaitCancellation()
    }
}

/**
 * The reading panes' renderer-crash owner (`DesktopReadingPaneEngine(onRendererGone = ...)`): [DesktopReadingRecovery]
 * over the global Koin graph, built on the first crash (a renderer can only die once a pane exists, i.e. after startup).
 */
fun desktopRendererRecovery(): (windowId: String) -> Unit {
    val recovery by lazy { DesktopReadingRecovery.forWindows(KoinPlatform.getKoin()) }
    return { windowId -> recovery(windowId) }
}

/** A desktop has no tilt sensor ([DesktopReadingHostPlatform.tiltAvailable] is false), so a session does nothing. */
object DesktopNoTiltScroll : TiltScrollSession {
    override fun setAppActive(active: Boolean) = Unit
    override fun start(scope: CoroutineScope) = Unit
    override fun stop() = Unit
}

/** The desktop [AppDialogHost]: the Koin [ExternalLinks], and dialogs may always render (no native presentations). */
class DesktopAppDialogHost(private val koin: () -> Koin = { KoinPlatform.getKoin() }) : AppDialogHost {
    override fun newExternalLinks(): ExternalLinks = koin().get()

    @Composable
    override fun rememberCanRenderDialogs(): () -> Boolean = remember { { true } }
}

/**
 * The desktop [ReadingHostPlatform]: window facts from [window], pickers and sharing from Koin (stubs until D3b), the
 * AWT clipboard and desktop browser where the JVM has them. The toolbar's [ToolbarDeviceState] reads the same
 * [DeviceLocalPrefs] and [NightModeState] singletons as the Koin graph, so the toolbar and the app agree on night mode.
 */
class DesktopReadingHostPlatform(
    private val window: DesktopWindowHandle,
    private val koin: () -> Koin = { KoinPlatform.getKoin() },
) : ReadingHostPlatform {
    override fun openUrl(url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "openUrl: no URL (no system settings page on the desktop)")
            return
        }
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                Log.w(TAG, "openUrl: no desktop browser available for $url")
            }
        } catch (e: Exception) {
            Log.w(TAG, "openUrl failed for $url", e)
        }
    }

    override val settingsUrl: String get() = ""

    override fun setStatusBarHidden(hidden: Boolean) = Unit

    override fun isPortrait(): Boolean = window.sizeDp.let { it.height > it.width }

    override fun isRightToLeft(): Boolean =
        DesktopUiLanguage.isRtl(koin().getOrNull<IosStringTableHolder>()?.current?.tag ?: "en")

    override fun screenScale(): Float = window.density

    override fun versionName(): String = koin().get<DesktopAppEnvironment>().versionName

    override fun tiltAvailable(): Boolean = false

    override fun formatHistoryTimestamp(epochMillis: Long): String {
        val tag = koin().getOrNull<IosStringTableHolder>()?.current?.tag ?: "en"
        return SimpleDateFormat("h:mm a, E d MMM ", Locale.forLanguageTag(tag)).format(Date(epochMillis))
    }

    override fun copyToPasteboard(text: String) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                Log.w(TAG, "copyToPasteboard: no clipboard in a headless JVM")
                return
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            Log.w(TAG, "copyToPasteboard failed", e)
        }
    }

    override fun beginBackgroundTask(onExpire: () -> Unit): ULong = NO_BACKGROUND_TASK

    override fun endBackgroundTask(id: ULong) = Unit

    override val invalidBackgroundTaskId: ULong get() = NO_BACKGROUND_TASK

    override fun newDocumentPicker(): DocumentPicker = koin().get()

    override fun newCsvSinkOpener(): (DocumentRef) -> Sink? {
        val files = koin().get<DesktopBookmarkCsvFiles>()
        return files::openSink
    }

    override fun csvTimestamp(): String = koin().get<DesktopBookmarkCsvFiles>().timestamp()

    override fun newImagePicker(): ImagePicker = koin().get()

    override fun newTextShare(): TextShare = koin().get()

    override fun newToolbarDeviceState(): ToolbarDeviceState {
        val k = koin()
        val nightState = k.get<NightModeState>()
        val prefs = k.get<DeviceLocalPrefs>()
        return ToolbarDeviceState(
            deviceSettings = prefs,
            nightModeOn = { nightState.nightMode },
            // The mode key of the same store that NightModeState reads (iOS: IosNightModeState.mode == "manual").
            nightModeManual = {
                prefs.getString(DesktopNightModeStub.NIGHT_MODE_MODE_KEY, DesktopNightModeStub.DEFAULT_MODE) == "manual"
            },
        )
    }
}
