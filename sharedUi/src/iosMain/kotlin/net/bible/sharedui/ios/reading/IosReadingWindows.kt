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
import net.bible.service.common.DisplayColorMode
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.android.view.activity.page.SwipeAction
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.ReadingLinkHandler
import net.bible.sharedui.reading.session.SessionEventRelay
import net.bible.sharedui.reading.session.activeLanguagesJson
import net.bible.sharedui.reading.session.readingVerseChooser
import net.bible.sharedui.reading.session.watchSettingKeys
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.IosReadingWindowSurface
import net.bible.sharedui.webview.WkWebViewJsHost
import net.bible.sharedui.webview.activeLanguagesScript
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
        val platform: IosDocumentLoaderPlatform,
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

    /**
     * Binds the assembly to the reading host. A second bind (a new host) drops the previous binding's window
     * subscription; sessions of the old host are released first.
     */
    fun bind(host: IosReadingHost, scope: CoroutineScope, koin: Koin, nightModeState: NightModeState) {
        unbind()
        val b = Binding(host, scope, koin, nightModeState)
        binding = b
        // The languages of the installed Bibles (Android's `getActiveLanguages`), read when each window's WebView is created.
        IosReadingWebViews.activeLanguages = ::installedLanguagesJson
        b.subscriptions.add(koin.get<WindowStateServiceImpl>().windowChanges.subscribe { change ->
            if (change == WindowChange.WindowsChanged) {
                // Minimising or restoring a window shows or hides the restore row, which is an offsets term.
                scope.launch(Dispatchers.Main) { releaseClosedWindows(); b.host.offsets.refresh() }
            }
        })
        // The INITIAL CONTENT LOAD of the restored windows, exactly as Android runs it once when the reading host is
        // built (NavHostComposeActivity.readingViewHost: windowSync.reloadAllWindows(true) -> Window.updateOrScroll ->
        // loadText). A window restored from the database has no page change to trigger its first load; without this its
        // BibleView gets the client-ready handshake and then stays blank. Plain-model work: loadText hands the rest to IO
        // and waits for the window's view, which the pane assembly assigns.
        koin.get<WindowControl>().windowSync.reloadAllWindows(true)
    }

    private fun installedLanguagesJson(): String =
        activeLanguagesJson(SwordDocumentFacade.bibles.map { it.bookMetaData.language.code })

    /** The document offsets changed (safe area, search sheet, agent log, speak bar): re-send `set_offsets` to every window. */
    fun offsetsChanged() {
        for (a in assemblies.values) {
            a.session.emit(BibleViewEvents.setOffsets(a.platform.topOffset, a.platform.bottomOffset, true, false))
        }
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

    /** The reading gestures of the bound host, for the pane factory; null while no host is bound. */
    fun gesturesFor(@Suppress("UNUSED_PARAMETER") windowId: String): IosReadingGestures? = binding?.host?.gestures

    /**
     * Carries out a decided swipe on [windowId]: chapter moves go through the window's [ReadingBridge] (the same stale-page
     * guard a JS-initiated chapter change has), page moves emit the scroll events Android's volume keys emit.
     */
    fun performSwipe(windowId: String, action: SwipeAction) {
        val a = assemblies[windowId] ?: return
        when (action) {
            SwipeAction.NextChapter -> a.bridge.goToNextChapter()
            SwipeAction.PreviousChapter -> a.bridge.goToPreviousChapter()
            SwipeAction.PageDown -> a.session.emit(BibleViewEvents.scrollDown())
            SwipeAction.PageUp -> a.session.emit(BibleViewEvents.scrollUp())
            SwipeAction.None -> {}
        }
    }

    /** The window's relay platform (test view of what the relay forwards); null when not assembled. */
    internal fun relayPlatformFor(windowId: String): IosSessionEventRelayPlatform? = assemblies[windowId]?.relayPlatform

    internal fun hasAssembly(windowId: String): Boolean = assemblies.containsKey(windowId)

    /** True when [windowId] names a window that exists and is not closed; only such windows get a session or a WebView. */
    fun isOpen(windowId: String): Boolean {
        val b = binding ?: return false
        val w = b.koin.get<WindowControl>().windowRepository.getWindow(IdType(windowId))
        return w != null && w.windowState != WindowLayout.WindowState.CLOSED
    }

    /**
     * Night mode, colour mode or the animation setting changed: re-send `set_config` to every window through its
     * relay platform. The page is not reloaded (no flash, scroll position kept) and nothing is loaded again; the
     * offsets are untouched (they travel in their own `set_offsets`).
     */
    fun nightModeChanged() {
        for (a in assemblies.values) a.relayPlatform.updateConfig()
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
            surface = IosReadingWindowSurface(host, links = koin.get<ExternalLinks>()),
            navigator = b.host.navigator,
        )
        try {
            session.hostSheets = b.host.hostState
            val bridgeRef = arrayOfNulls<ReadingBridge>(1)
            session.keyActions = IosReadingKeyActions(
                hostState = b.host.hostState,
                openSearch = { b.host.navigator.openSearch(null) },
                openOverflow = { b.host.toolbarHost.openOverflowMenu() },
                copyCurrentSelection = { bridgeRef[0]?.copyCurrentSelection() },
            )
            session.verseChooser = readingVerseChooser(b.host.hostState)
            val settings = koin.get<AppSettings>()
            // Known follow-up (Stream R): a Window object re-created after a workspace restore is not re-bound here
            // (Android rebinds in BibleViewFactory.getOrCreateBibleView); this session keeps the window it was built for.
            val platform = IosDocumentLoaderPlatform(
                window = { session.window },
                windowControl = { windowControl },
                settings = { settings },
                nightModeState = { b.nightModeState },
                llmConfigured = { runCatching { koin.get<PromptService>().configured.value }.getOrDefault(false) },
                offsets = { b.host.offsets },
            )
            // The loader is the session's paging seam: it must exist before the bridge can receive a call.
            val loader = DocumentLoader(session, platform).also { session.paging = it }
            val bridge = ReadingBridge(session, viewIsCurrent = { windowControl.activeWindow.id.toString() == windowId })
            bridgeRef[0] = bridge
            // The same steps Android takes (BibleView.openEpubLink): resolve the key off the main thread, then showLink.
            val linkHandler = ReadingLinkHandler(session) { book, backend, keyStr, idStr -> bridge.openEpubLink(book, backend, keyStr, idStr) }
            val relayPlatform = IosSessionEventRelayPlatform { loader.updateConfig() }
            val relay = SessionEventRelay(session, loader, relayPlatform)
            relay.start()
            // Window.loadText delivers documents to window.bibleView; without one it times out after 5 s.
            val view = IosWindowView(session, loader, beforeLoad = {
                // Books may have been installed since the WebView was created: the page reads the global lazily.
                host.post { host.evaluate(activeLanguagesScript(installedLanguagesJson()), null) }
            })
            window.bibleView = view
            if (window.takeLoadPending()) window.loadText()
            return Assembly(session, loader, relay, bridge, relayPlatform, platform, linkHandler)
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
 * Follows the display modes that need a `set_config` and a theme refresh: night mode, colour mode and the animation
 * setting. Two signals, because the sources differ: night mode is a `UserDefaults` key (`NSUserDefaultsDidChange`, plus
 * app-active), while colour mode and animations live in the database-backed [AppSettings], whose writes post no
 * platform notification; those arrive through [settingsChanges] (`SettingsStore.changes`). [dark] and [monochrome]
 * are Compose state for the theme and the reading host; [onChange] fires once per real change of any of the modes.
 */
@kotlin.native.HiddenFromObjC
class IosNightModeObserver(
    private val state: NightModeState,
    private val settings: AppSettings? = null,
    private val settingsChanges: kotlinx.coroutines.flow.Flow<String>? = null,
    private val onChange: () -> Unit = {},
) {
    private val darkState = androidx.compose.runtime.mutableStateOf(state.nightMode)
    private val monochromeState = androidx.compose.runtime.mutableStateOf(readMonochrome())
    private var lastModes: List<Any?> = readModes()
    val dark: androidx.compose.runtime.State<Boolean> get() = darkState

    /** Colour mode other than NORMAL: the predicate `IosDocumentLoaderPlatform` sends as `monochromeMode`. */
    val monochrome: androidx.compose.runtime.State<Boolean> get() = monochromeState
    private val observers = mutableListOf<Any>()
    private var settingsWatch: kotlinx.coroutines.Job? = null

    private fun readMonochrome(): Boolean =
        settings != null && (DisplayColorMode.fromValue(settings.getString("display_color_mode", null)) ?: DisplayColorMode.NORMAL) != DisplayColorMode.NORMAL

    private fun readModes(): List<Any?> =
        listOf(settings?.getString("display_color_mode", null), settings?.getBoolean("disable_animations", false))

    /** Re-reads the modes; returns the current night value. */
    fun refresh(): Boolean {
        val now = state.nightMode
        val modes = readModes()
        val changed = now != darkState.value || modes != lastModes
        lastModes = modes
        darkState.value = now
        monochromeState.value = readMonochrome()
        if (changed) onChange()
        return now
    }

    /**
     * Starts the notification observers and, when [settingsChanges] was given, the settings watch on [scope]. A settings
     * source without a scope would silently never fire, so that combination is refused.
     */
    fun start(scope: kotlinx.coroutines.CoroutineScope? = null) {
        require(scope != null || settingsChanges == null) { "IosNightModeObserver.start needs a scope when settingsChanges is given" }
        if (observers.isNotEmpty()) return
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        for (name in listOf(platform.Foundation.NSUserDefaultsDidChangeNotification, platform.UIKit.UIApplicationDidBecomeActiveNotification)) {
            observers += center.addObserverForName(name, null, platform.Foundation.NSOperationQueue.mainQueue) { refresh() }
        }
        if (scope != null && settingsChanges != null) {
            settingsWatch = watchSettingKeys(settingsChanges, DISPLAY_MODE_KEYS, scope) { refresh() }
        }
    }

    fun stop() {
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
        settingsWatch?.cancel()
        settingsWatch = null
    }

    private companion object {
        /** The [AppSettings] keys whose writes change a display mode. */
        val DISPLAY_MODE_KEYS = setOf("display_color_mode", "disable_animations")
    }
}
