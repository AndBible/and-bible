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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.ios.IosPending
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.ReadingKeyActions
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.ReadingLinkHandler
import net.bible.sharedui.reading.session.SessionEventRelay
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.IosReadingWindowSurface
import net.bible.sharedui.webview.PendingIosReadingNavigator
import net.bible.sharedui.webview.WkWebViewJsHost
import org.koin.core.Koin

/**
 * Per-window reading session assembly on iOS (the counterpart of Android's `BibleView` constructor and
 * `listenEvents`): for each window id, once, a [BibleWindowSession] over the window's cached WKWebView with its
 * [DocumentLoader], [ReadingBridge], [ReadingLinkHandler] and a started [SessionEventRelay].
 *
 * Main thread only (WebKit and the web-view cache are). Call [bind] once the window repository exists and before
 * the first [sessionFor]. A window that is closed is released on the next window change; [release] disposes the
 * session, stops its relay and drops the WebView (with its `"bridge"` handler), so a reopened window assembles anew.
 */
@kotlin.native.HiddenFromObjC
object IosReadingWindows {
    private class Assembly(
        val session: BibleWindowSession,
        val loader: DocumentLoader,
        val relay: SessionEventRelay,
        val bridge: ReadingBridge,
        val relayPlatform: IosSessionEventRelayPlatform,
        @Suppress("unused") val linkHandler: ReadingLinkHandler,
    ) {
    }

    private class Binding(
        val host: IosReadingHost,
        val scope: CoroutineScope,
        val koin: Koin,
        val nightModeState: NightModeState,
    ) {
        val subscriptions = Subscriptions()
    }

    private val assemblies = mutableMapOf<String, Assembly>()
    private var binding: Binding? = null

    /** The pending [ReadingKeyActions]: iOS has no hardware-key reading shortcuts wired yet. */
    private val pendingKeyActions = object : ReadingKeyActions {
        override fun openDrawer() = IosPending.log("ReadingKeyActions.openDrawer")
        override fun openOverflowMenu() = IosPending.log("ReadingKeyActions.openOverflowMenu")
        override fun showWorkspaceSheetIfHosted(): Boolean { IosPending.log("ReadingKeyActions.showWorkspaceSheetIfHosted"); return false }
        override fun copySelection() = IosPending.log("ReadingKeyActions.copySelection")
        override fun searchIfHosted(): Boolean { IosPending.log("ReadingKeyActions.searchIfHosted"); return false }
        override fun crashAllBibleViews() = IosPending.log("ReadingKeyActions.crashAllBibleViews")
    }

    /**
     * Binds the assembly to the reading host. A second bind (a new host) drops the previous binding's window
     * subscription; sessions of the old host are released first.
     */
    fun bind(host: IosReadingHost, scope: CoroutineScope, koin: Koin, nightModeState: NightModeState) {
        unbind()
        val b = Binding(host, scope, koin, nightModeState)
        binding = b
        b.subscriptions.add(koin.get<WindowStateServiceImpl>().windowChanges.subscribe { change ->
            if (change == WindowChange.WindowsChanged) {
                scope.launch(Dispatchers.Main) { releaseClosedWindows() }
            }
        })
    }

    private fun unbind() {
        for (id in assemblies.keys.toList()) release(id)
        binding?.subscriptions?.cancelAll()
        binding = null
    }

    private fun requireBinding(): Binding = checkNotNull(binding) { "IosReadingWindows.bind has not been called" }

    /** The night parameter the next page load uses: the live [NightModeState], never a constant. */
    val nightMode: Boolean get() = binding?.nightModeState?.nightMode ?: false

    /** The window's session, assembled on first use. */
    fun sessionFor(windowId: String): BibleWindowSession = assembly(windowId).session

    /** The window's bridge (the one `sessionFor` assembled), for the WebView's message handler. */
    fun bridgeFor(windowId: String): BibleBridge = assembly(windowId).bridge

    /** The window's relay platform (test view of what the relay forwards); null when not assembled. */
    internal fun relayPlatformFor(windowId: String): IosSessionEventRelayPlatform? = assemblies[windowId]?.relayPlatform

    internal fun hasAssembly(windowId: String): Boolean = assemblies.containsKey(windowId)

    /** True when [windowId] names a window that exists and is not closed; only such windows get a session or a WebView. */
    fun isOpen(windowId: String): Boolean {
        val b = binding ?: return false
        val w = b.koin.get<WindowControl>().windowRepository.getWindow(IdType(windowId))
        return w != null && w.windowState != WindowLayout.WindowState.CLOSED
    }

    /** Night mode changed: re-send `set_config` to every window. */
    fun nightModeChanged() {
        for (a in assemblies.values) a.loader.updateConfig()
    }

    private fun assembly(windowId: String): Assembly = assemblies.getOrPut(windowId) { assemble(windowId) }

    private fun assemble(windowId: String): Assembly {
        val b = requireBinding()
        val koin = b.koin
        val windowControl = koin.get<WindowControl>()
        // Refuse before creating anything: no WebView entry, no session, nothing cached for a closed or missing window.
        val window = windowControl.windowRepository.getWindow(IdType(windowId))
        check(window != null && window.windowState != WindowLayout.WindowState.CLOSED) { "window $windowId is closed or missing" }
        val webView = IosReadingWebViews.webViewFor(windowId)
        val host = WkWebViewJsHost(webView)
        val session = BibleWindowSession(
            window = window,
            jsChannel = BibleViewJsChannel(host) { nsLog("bridge[$windowId]: $it") },
            hostScope = { b.scope },
            surface = IosReadingWindowSurface(host),
            navigator = PendingIosReadingNavigator,
        )
        try {
            session.hostSheets = b.host.hostState
            session.keyActions = pendingKeyActions
            session.verseChooser = { IosPending.log("verseChooser"); null }
            val settings = koin.get<AppSettings>()
            val platform = IosDocumentLoaderPlatform(
                window = { session.window },
                windowControl = { windowControl },
                settings = { settings },
                nightModeState = { b.nightModeState },
                llmConfigured = { runCatching { koin.get<PromptService>().configured.value }.getOrDefault(false) },
            )
            // The loader is the session's paging seam: it must exist before the bridge can receive a call.
            val loader = DocumentLoader(session, platform).also { session.paging = it }
            val bridge = ReadingBridge(session, viewIsCurrent = { windowControl.activeWindow.id.toString() == windowId })
            val linkHandler = ReadingLinkHandler(session) { _, _, _, _ -> IosPending.log("openEpubLink") }
            val relayPlatform = IosSessionEventRelayPlatform { loader.updateConfig() }
            val relay = SessionEventRelay(session, loader, relayPlatform)
            relay.start()
            // Window.loadText delivers documents to window.bibleView; without one it times out after 5 s.
            val view = IosWindowView(session, loader)
            window.bibleView = view
            if (window.takeLoadPending()) window.loadText()
            return Assembly(session, loader, relay, bridge, relayPlatform, linkHandler)
        } catch (t: Throwable) {
            // Nothing is in the map yet: dispose what was built so no scope or WebView entry outlives the failure.
            session.dispose()
            IosReadingWebViews.release(windowId)
            throw t
        }
    }

    /** Disposes the window's session, stops its relay and drops its WebView (removing the `"bridge"` handler). */
    fun release(windowId: String) {
        val a = assemblies.remove(windowId)
        if (a != null) {
            val w = a.session.windowOrNull
            if (w?.bibleView is IosWindowView) w.bibleView = null
            a.relay.stop()
            a.session.dispose()
        }
        IosReadingWebViews.release(windowId)
    }

    private fun releaseClosedWindows() {
        val b = binding ?: return
        val repository = b.koin.get<WindowControl>().windowRepository
        for (id in assemblies.keys.toList()) {
            val w = repository.getWindow(IdType(id))
            if (w == null || w.windowState == WindowLayout.WindowState.CLOSED) release(id)
        }
    }

    internal fun resetForTest() = unbind()
}

/**
 * Follows [NightModeState] (a plain Boolean read from `UserDefaults`, no observable of its own): [dark] is
 * refreshed on `NSUserDefaultsDidChange` and when the app becomes active, and [onChange] fires on a real change.
 */
@kotlin.native.HiddenFromObjC
class IosNightModeObserver(private val state: NightModeState, private val onChange: (Boolean) -> Unit = {}) {
    private val darkState = androidx.compose.runtime.mutableStateOf(state.nightMode)
    val dark: androidx.compose.runtime.State<Boolean> get() = darkState
    private val observers = mutableListOf<Any>()

    /** Re-reads the state; returns the current value. */
    fun refresh(): Boolean {
        val now = state.nightMode
        if (now != darkState.value) {
            darkState.value = now
            onChange(now)
        }
        return now
    }

    fun start() {
        if (observers.isNotEmpty()) return
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        for (name in listOf(platform.Foundation.NSUserDefaultsDidChangeNotification, platform.UIKit.UIApplicationDidBecomeActiveNotification)) {
            observers += center.addObserverForName(name, null, platform.Foundation.NSOperationQueue.mainQueue) { refresh() }
        }
    }

    fun stop() {
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
    }
}
