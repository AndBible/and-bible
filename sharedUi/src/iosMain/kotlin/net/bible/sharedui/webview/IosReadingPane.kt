@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.collectLatest
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.host.reading.HostReadingGestures
import net.bible.sharedui.host.reading.HostReadingWindows
import net.bible.sharedui.host.reading.HostSelectionMenu
import net.bible.sharedui.ios.reading.IosSelectionMessageHandler
import net.bible.sharedui.host.reading.SELECTION_HANDLER_NAME
import net.bible.sharedui.host.reading.SELECTION_USER_SCRIPT
import net.bible.sharedui.ios.reading.insertSelectionMenu
import platform.UIKit.UIMenuBuilderProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.readingShortcutScript
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.reading.session.BibleWindowSession
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.Foundation.setValue
import platform.UIKit.UIColor
import platform.UIKit.UIKeyboardHIDUsageKeyboardA
import platform.UIKit.UIKeyboardHIDUsageKeyboardEscape
import platform.UIKit.UIKeyboardHIDUsageKeyboardZ
import platform.UIKit.UIKeyboardHIDUsageKeyboardRightArrow
import platform.UIKit.UIKeyboardHIDUsageKeyboardLeftArrow
import platform.UIKit.UIKeyboardHIDUsageKeyboardDownArrow
import platform.UIKit.UIKeyboardHIDUsageKeyboardUpArrow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlinx.cinterop.ObjCObjectBase.OverrideInit

/**
 * WKWebView that tells [onAttached] when it gets a window. `BibleViewJsChannel` queues calls while the
 * host is detached and only runs them on a later call or a flush; Android's BibleView flushes when it
 * attaches, so this does the same (the owner sets [onAttached] to `jsChannel.flush()`).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class ReadingWkWebView @OverrideInit constructor(frame: CValue<CGRect>, configuration: WKWebViewConfiguration) :
    WKWebView(frame, configuration) {
    var onAttached: (() -> Unit)? = null
    var isEditing: () -> Boolean = { false }
    private val swallowedPresses = mutableSetOf<platform.UIKit.UIPress>()

    override fun pressesBegan(presses: Set<*>, withEvent: platform.UIKit.UIPressesEvent?) {
        val unhandled = presses.filterNot { value ->
            val press = value as? platform.UIKit.UIPress ?: return@filterNot false
            val key = press.key ?: return@filterNot false
            // Esc (#4003): the page already received it and routes it to back itself (READING_ESCAPE_SCRIPT), or its
            // editor used it; bubbling it on would let Compose's scene input go back a second time.
            if (key.keyCode == UIKeyboardHIDUsageKeyboardEscape) {
                swallowedPresses.add(press)
                return@filterNot true
            }
            if (isEditing()) return@filterNot false
            val code = when (key.keyCode) {
                in UIKeyboardHIDUsageKeyboardA..UIKeyboardHIDUsageKeyboardZ ->
                    "Key" + ('A' + (key.keyCode - UIKeyboardHIDUsageKeyboardA).toInt())
                UIKeyboardHIDUsageKeyboardRightArrow -> "ArrowRight"
                UIKeyboardHIDUsageKeyboardLeftArrow -> "ArrowLeft"
                UIKeyboardHIDUsageKeyboardDownArrow -> "ArrowDown"
                UIKeyboardHIDUsageKeyboardUpArrow -> "ArrowUp"
                else -> return@filterNot false
            }
            val flags = key.modifierFlags
            net.bible.sharedui.reading.readingShortcut(
                code,
                ctrl = flags and platform.UIKit.UIKeyModifierControl != 0L,
                alt = flags and platform.UIKit.UIKeyModifierAlternate != 0L,
                meta = flags and platform.UIKit.UIKeyModifierCommand != 0L,
            ) ?: return@filterNot false
            // Native is the sole owner of modified iOS chords. JS checks its editor synchronously;
            // its DOM listener ignores these chords even if WebKit also delivers a keydown.
            deliverReadingShortcut(code,
                ctrl = flags and platform.UIKit.UIKeyModifierControl != 0L,
                alt = flags and platform.UIKit.UIKeyModifierAlternate != 0L,
            )
            swallowedPresses.add(press)
            true
        }.toSet()
        if (unhandled.isNotEmpty()) super.pressesBegan(unhandled, withEvent)
    }

    override fun pressesEnded(presses: Set<*>, withEvent: platform.UIKit.UIPressesEvent?) {
        val unhandled = presses.filterNot { it is platform.UIKit.UIPress && swallowedPresses.remove(it) }.toSet()
        if (unhandled.isNotEmpty()) super.pressesEnded(unhandled, withEvent)
    }

    override fun pressesCancelled(presses: Set<*>, withEvent: platform.UIKit.UIPressesEvent?) {
        val unhandled = presses.filterNot { it is platform.UIKit.UIPress && swallowedPresses.remove(it) }.toSet()
        if (unhandled.isNotEmpty()) super.pressesCancelled(unhandled, withEvent)
    }

    internal fun deliverReadingShortcut(code: String, ctrl: Boolean, alt: Boolean) {
        evaluateJavaScript(readingShortcutScript(code, ctrl, alt)) { _, error ->
            if (error != null) nsLog("bridge: reading shortcut failed ${error.localizedDescription}")
        }
    }

    init {
        // UIKit's scroll-to-top (normally a status-bar tap) animated the reading page to the top of the loaded
        // window with no input reaching the page (seen on the iPad simulator); infinite scroll then kept
        // prepending chapters and the position notifier saved John 1:1 as the reading position. Off on every
        // device: the reading position moves only by the user's own scroll. Android has no such gesture.
        scrollView.scrollsToTop = false
    }

    /** Adds the AndBible rows to the system edit menu (see [insertSelectionMenu]); null leaves the menu as WebKit builds it. */
    var onBuildMenu: ((UIMenuBuilderProtocol) -> Unit)? = null

    override fun buildMenuWithBuilder(builder: UIMenuBuilderProtocol) {
        super.buildMenuWithBuilder(builder)
        try {
            onBuildMenu?.invoke(builder)
        } catch (t: Throwable) {
            nsLog("bridge: selection menu failure $t")
        }
    }

    /** Set by the pane composable that hosts this view now: refreshes its accessibility element (issue #3949). */
    var onWindowAttachForAccessibility: (() -> Unit)? = null

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        if (window != null) {
            try {
                onAttached?.invoke()
            } catch (t: Throwable) {
                nsLog("bridge: flush on attach failure $t")
            }
            onWindowAttachForAccessibility?.invoke()
        }
    }
}

/** One reading window's WebView plus the objects WebKit only holds weakly (spec §5 rule 2). */
internal class ReadingEntry(val webView: ReadingWkWebView, val navDelegate: IosNavDelegate) {
    var handler: IosBridgeMessageHandler? = null

    /** How many `"bridge"` script-message handlers this WebView has registered (0 or 1; WebKit throws on a duplicate name). */
    var handlerCount: Int = 0

    /** Set when wiring the session threw; the pane stays empty instead of retrying on every recomposition. */
    var failed: Boolean = false

    /** The `"selection"` script-message handler (held strongly: WebKit holds it weakly); null when the window has no selection menu. */
    var selectionHandler: IosSelectionMessageHandler? = null
    var selectionScriptInstalled = false

    /** The pane's gesture recognisers once attached (see [attachReadingGestures]); detached when the window is released. */
    var gestureRecognisers: IosReadingGestureRecognisers? = null

    /** The page background last applied to the WebView (ARGB), seeded before the entry is published. */
    var backgroundArgb: Int? = null
}

/** The [UIColor] for an ARGB int (alpha in the top byte, as Android's `Color` ints and `TextDisplaySettings.Colors` hold it). */
internal fun uiColorFromArgb(argb: Int): UIColor = UIColor(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = ((argb shr 24) and 0xFF) / 255.0,
)

/**
 * Process-wide WKWebView cache for real reading windows, keyed by window id (same rules as the PoC's
 * holder: one WKWebView per window, re-parented, never recreated). Separate from the PoC's cache.
 *
 * Main thread only (checked): the map is unsynchronized and WebKit is main-thread bound.
 *
 * The WKWebView exists before the session: build the session's `JsHost` from [webViewFor], then the
 * pane factory ([iosReadingPaneFactory]) wires the bridge handler once it can ask `sessionFor`.
 */
@kotlin.native.HiddenFromObjC
@OptIn(ExperimentalForeignApi::class)
object IosReadingWebViews {
    private val entries = mutableMapOf<String, ReadingEntry>()

    /**
     * Bumped when a window's web view is dropped ([release]); read by the pane factory, so a pane whose window was rebuilt
     * with a new web view ([IosPaneLiveness]) recomposes and hosts the new one.
     */
    internal var releases by mutableIntStateOf(0)
        private set

    /**
     * Probes the wired panes when the app becomes active and rebuilds dead or hung ones (issue #4004). Only panes that
     * have loaded something are probed (a web view with no URL has no page that could have died) and that are on screen
     * (`window != null`): hidden, minimised or covered panes are probed on a later activation when they are visible.
     */
    private val liveness = IosPaneLiveness(
        panes = { entries.filterValues { it.handlerCount > 0 && it.webView.URL != null && it.webView.window != null }.mapValues { it.value.webView } },
        recover = { windowId -> HostReadingWindows.rebuild(windowId) },
    )

    /** The installed Bibles' languages as a JSON list, read when a window's WebView is created (set by the reading assembly). */
    internal var activeLanguages: () -> String = { "[\"en\"]" }

    /** Loads the bundle into a WebView; a test seam (the test binary has no `bibleview-js` bundle). */
    internal var bundleLoader: (WKWebView, String, Boolean) -> Unit = { webView, lang, dark -> loadBibleBundle(webView, lang, dark) }

    /** The window's WKWebView, created (without bridge handler, not yet loaded) on first use. */
    fun webViewFor(windowId: String): WKWebView = entry(windowId).webView

    /**
     * Paints the window's WKWebView and its scroll view with [argb] (Android's `BibleView.setBackgroundColor`):
     * BibleView-JS paints no page background of its own, so without this the WebView stays system white and a night
     * page shows light-on-white (NM-1). Callable from any thread (runs on the main thread); a window without a
     * WebView entry, or one released before the dispatch runs, is ignored. `opaque` is turned off so the colour
     * set here shows through instead of WebKit's own white.
     */
    fun applyBackground(windowId: String, argb: Int) {
        if (NSThread.isMainThread) {
            applyBackgroundNow(windowId, argb)
        } else {
            dispatch_async(dispatch_get_main_queue()) { applyBackgroundNow(windowId, argb) }
        }
    }

    private fun applyBackgroundNow(windowId: String, argb: Int) {
        val entry = entries[windowId] ?: return
        applyBackground(entry, argb)
    }

    private fun applyBackground(entry: ReadingEntry, argb: Int) {
        if (entry.backgroundArgb == argb) return
        entry.backgroundArgb = argb
        val color = uiColorFromArgb(argb)
        // An opaque WKWebView paints its own white behind the page, whatever backgroundColor says (Mac run 8 of NM-1).
        entry.webView.opaque = false
        entry.webView.underPageBackgroundColor = color
        entry.webView.backgroundColor = color
        entry.webView.scrollView.backgroundColor = color
    }

    internal fun rekey(fromId: String, toId: String) {
        checkMain()
        if (fromId == toId) return
        val entry = entries.remove(fromId) ?: return
        check(!entries.containsKey(toId))
        entry.navDelegate.windowId = toId
        entry.webView.setValue("bible-webview-$toId", forKey = "accessibilityIdentifier")
        entry.gestureRecognisers?.detach()
        entry.gestureRecognisers = null
        if (entry.selectionHandler != null) {
            entry.webView.configuration.userContentController.removeScriptMessageHandlerForName(SELECTION_HANDLER_NAME)
            entry.selectionHandler = null
        }
        entry.webView.onBuildMenu = null
        entries[toId] = entry
    }

    /** Number of `"bridge"` handlers registered on the window's WebView (0 once released). */
    internal fun bridgeHandlerCount(windowId: String): Int = entries[windowId]?.handlerCount ?: 0

    /** Whether a WebView entry is cached for the window (without creating one). */
    internal fun hasEntry(windowId: String): Boolean = entries.containsKey(windowId)

    /**
     * Drops the window's WebView for good (window closed): unregisters the handler. The owner must also
     * `dispose()` the window's [BibleWindowSession]; this does not.
     */
    fun release(windowId: String) {
        checkMain()
        val entry = entries.remove(windowId) ?: return
        releases++
        removeConsoleHandler(entry.webView.configuration.userContentController)
        entry.webView.configuration.userContentController.removeScriptMessageHandlerForName(READING_ESCAPE_HANDLER_NAME)
        if (entry.handlerCount > 0) {
            entry.webView.configuration.userContentController.removeScriptMessageHandlerForName("bridge")
            entry.handlerCount = 0
        }
        entry.handler = null
        if (entry.selectionHandler != null) {
            entry.webView.configuration.userContentController.removeScriptMessageHandlerForName(SELECTION_HANDLER_NAME)
            entry.selectionHandler = null
        }
        entry.webView.onBuildMenu = null
        entry.gestureRecognisers?.detach()
        entry.gestureRecognisers = null
        // Detach the dropped web view: a window rebuilt under the same id must not hear its late termination callbacks.
        entry.navDelegate.released = true
        entry.webView.navigationDelegate = null
        entry.webView.stopLoading()
        entry.webView.removeFromSuperview()
        // The closure holds the session (and through it the JS channel); drop it so the WebView does not keep them alive.
        entry.webView.onAttached = null
        entry.webView.isEditing = { false }
        entry.webView.bibleSchemeHandler?.currentDocument = { null }
    }

    internal fun resetForTest() {
        for (id in entries.keys.toList()) release(id)
        bundleLoader = { webView, lang, dark -> loadBibleBundle(webView, lang, dark) }
        activeLanguages = { "[\"en\"]" }
    }

    private fun checkMain() = check(NSThread.isMainThread) { "IosReadingWebViews is main-thread only" }

    internal fun entry(windowId: String): ReadingEntry {
        checkMain()
        IosWebContentKillHook.installIfRequested { entries.mapValues { it.value.webView } }
        liveness.installOnBecomeActive()
        return entries.getOrPut(windowId) {
            val navDelegate = IosNavDelegate(windowId)
            val webView = createBibleWkWebView(windowId, navDelegate, null, activeLanguages) {
                ReadingWkWebView(CGRectMake(0.0, 0.0, 0.0, 0.0), it)
            } as ReadingWkWebView
            webView.configuration.userContentController.addUserScript(
                WKUserScript(
                    source = "window.__abNativeReadingChords = true;",
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
            // #4003: Esc with the Bible text focused reaches the shared back through the page (see IosReadingEscape.kt).
            webView.configuration.userContentController.addUserScript(
                WKUserScript(
                    source = READING_ESCAPE_SCRIPT,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
            webView.configuration.userContentController.addScriptMessageHandler(sharedReadingEscapeHandler, READING_ESCAPE_HANDLER_NAME)
            ReadingEntry(webView, navDelegate).also {
                // The first set_config arrives after creation, possibly after attachment. Seed all WebKit
                // backing layers now, before the cached view can be returned to a UIKitView or a JsHost.
                applyBackground(it, HostReadingWindows.pageBackgroundArgb(windowId))
            }
        }
    }

    /**
     * Adds the bridge handler and loads the bundle, once per window. [dark] is only the attach-time initial value of
     * the `night` URL parameter; live night mode reaches the page through `set_config`.
     */
    internal fun attachSession(
        windowId: String,
        session: BibleWindowSession,
        bridge: BibleBridge,
        dark: Boolean,
        selectionMenu: HostSelectionMenu? = null,
    ) {
        val entry = entry(windowId)
        if (entry.handler != null) {
            if (selectionMenu != null) attachSelectionMenu(entry, windowId, selectionMenu)
            return
        }
        val log: (String) -> Unit = { msg -> nsLog("bridge[$windowId]: $msg") }
        val dispatcher = BridgeDispatcher(
            bridge,
            { callId -> session.emit(BibleViewEvents.response(callId, null)) },
            log,
        )
        val handler = IosBridgeMessageHandler(dispatcher)
        entry.webView.onAttached = { session.jsChannel.flush() }
        entry.webView.isEditing = { session.editingTextInJs }
        // `/epub/<path>` reads the EPUB this window shows (Android `EpubResourcesAssetHandler`).
        entry.webView.bibleSchemeHandler?.currentDocument = { session.firstDocument }
        entry.handler = handler // strong reference: WebKit holds the handler weakly
        entry.webView.configuration.userContentController.addScriptMessageHandler(handler, "bridge")
        entry.handlerCount++
        if (selectionMenu != null) attachSelectionMenu(entry, windowId, selectionMenu)
        val lang = NSLocale.currentLocale.languageCode.ifEmpty { "en" }
        bundleLoader(entry.webView, lang, dark)
    }
}

/**
 * Wires the text-selection menu to the window's WebView: the page reports its selection (debounced) to a native handler
 * that fills [menu]'s cache, and the web view's edit-menu hook adds the AndBible rows from that cache. Must run before the
 * bundle loads (the user script applies to later page loads).
 */
private fun attachSelectionMenu(entry: ReadingEntry, windowId: String, menu: HostSelectionMenu) {
    if (entry.selectionHandler != null) return
    val handler = IosSelectionMessageHandler(windowId, menu)
    val controller = entry.webView.configuration.userContentController
    if (!entry.selectionScriptInstalled) {
        controller.addUserScript(
            WKUserScript(
                source = SELECTION_USER_SCRIPT,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = true,
            ),
        )
        entry.selectionScriptInstalled = true
    }
    controller.addScriptMessageHandler(handler, SELECTION_HANDLER_NAME)
    entry.selectionHandler = handler
    entry.webView.onBuildMenu = { builder -> insertSelectionMenu(builder, windowId, menu) }
}

/** Attaches [gestures] to the window's WebView once (a second call, or a window without a handler, does nothing). */
internal fun attachReadingGestures(windowId: String, gestures: HostReadingGestures?) {
    if (gestures == null) return
    val entry = IosReadingWebViews.entry(windowId)
    if (entry.gestureRecognisers != null || entry.failed) return
    entry.gestureRecognisers = IosReadingGestureRecognisers(windowId, entry.webView, gestures).also { it.attach() }
}

/**
 * Wires the window's pane once: asks [sessionFor] / [bridgeFor] and registers the bridge handler with the
 * night parameter [nightMode] answers now. A failure is logged and parks the pane (no retry per recomposition).
 */
internal fun attachReadingPane(
    windowId: String,
    sessionFor: (windowId: String) -> BibleWindowSession,
    bridgeFor: ((windowId: String) -> BibleBridge)?,
    nightMode: () -> Boolean,
    isOpen: (windowId: String) -> Boolean = { true },
    selectionMenu: HostSelectionMenu? = null,
) {
    // A closed or missing window gets no entry, no session and no WebView, whatever the recomposition order.
    if (!isOpen(windowId)) return
    try {
        val session = sessionFor(windowId)
        val entry = IosReadingWebViews.entry(windowId)
        if (entry.failed) return
        val bridge = bridgeFor?.invoke(windowId) ?: ReadingBridge(session)
        IosReadingWebViews.attachSession(windowId, session, bridge, nightMode(), selectionMenu)
    } catch (t: Throwable) {
        IosReadingWebViews.entry(windowId).failed = true
        nsLog("bridge[$windowId]: reading pane wiring failed $t")
    }
}

/**
 * iOS [ReadingPaneFactory]: hosts the window's cached WKWebView in a `UIKitView`, wired to the real
 * session returned by [sessionFor] (called once per window, at first composition). [bridgeFor] supplies the
 * window's bridge (default: a bare `ReadingBridge`); [nightMode] feeds the page's `night` URL parameter.
 */
@kotlin.native.HiddenFromObjC
@OptIn(ExperimentalForeignApi::class)
fun iosReadingPaneFactory(
    sessionFor: (windowId: String) -> BibleWindowSession,
    bridgeFor: ((windowId: String) -> BibleBridge)? = null,
    nightMode: () -> Boolean = { false },
    isOpen: (windowId: String) -> Boolean = { true },
    /** The reading gestures (double tap, auto full screen, swipe) for a window's pane; null (no host bound) leaves the pane gesture-free. */
    gesturesFor: (windowId: String) -> HostReadingGestures? = { HostReadingWindows.gesturesFor(it) },
    /** The text-selection menu of the bound host; null (no host bound) leaves WebKit's edit menu untouched. */
    selectionMenu: () -> HostSelectionMenu? = { HostReadingWindows.selectionMenu },
): ReadingPaneFactory = { windowId, modifier ->
    attachReadingPane(windowId, sessionFor, bridgeFor, nightMode, isOpen, selectionMenu())
    if (isOpen(windowId)) attachReadingGestures(windowId, gesturesFor(windowId))
    // ReadingHost composes panes only for windows in the layout; this check covers the gap between the window
    // closing and the asynchronous release, so recomposition never recreates a released window's WebView.
    // Read so that a rebuilt window (new web view after a dead page, IosPaneLiveness) recomposes this pane.
    IosReadingWebViews.releases
    if (isOpen(windowId)) key(IosReadingWebViews.entry(windowId).webView) {
        val entry = IosReadingWebViews.entry(windowId)
        // Compose iOS caches a node's native accessibility children; after the cached WKWebView is re-parented into this
        // new holder that cache can stay empty, so VoiceOver (and XCUITest) lose the Bible text. Changing a semantics
        // value once the view is attached makes the accessibility sync rebuild this node's element (issue #3949).
        // Bumped when the view reaches a window (the moment the new holder is live) and, as a fallback, two frames after
        // this holder composed.
        var attachGeneration by remember { mutableIntStateOf(0) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(entry.webView) {
            val bump: () -> Unit = { attachGeneration++ }
            entry.webView.onWindowAttachForAccessibility = bump
            onDispose { if (entry.webView.onWindowAttachForAccessibility === bump) entry.webView.onWindowAttachForAccessibility = null }
        }
        LaunchedEffect(entry.webView) {
            withFrameNanos { }
            withFrameNanos { }
            attachGeneration++
        }
        // The incoming pane exists while the navigation crossfade still covers it, so native focusability can
        // cache an outdated beyond-bounds callback. RESUMED marks the route transition's completion; invalidate
        // the current pane after two frames so accessibility sync can bind it again.
        // At the same moment the web view takes first responder (unless it already has it or a text field elsewhere
        // does): WebKit shows no edit menu for a long-press selection that only made its content view first responder
        // during the gesture, so the first selection after launch or after a route return had no menu (issue #3968).
        LaunchedEffect(entry.webView, lifecycle) {
            lifecycle.currentStateFlow.collectLatest { state ->
                if (state == Lifecycle.State.RESUMED) {
                    withFrameNanos { }
                    withFrameNanos { }
                    attachGeneration++
                    entry.webView.window?.let { window ->
                        claimFirstResponderForSelection(entry.webView, firstResponderIn(window))
                    }
                }
            }
        }
        UIKitView(
            // Detach from any previous (disposed) interop holder before re-parenting the cached view.
            factory = { entry.webView.apply { removeFromSuperview() } },
            modifier = modifier.testTag("pane-$windowId")
                .semantics { this[PaneAttachGeneration] = attachGeneration }
                .wheelScrollsWebView(windowId, entry.webView),
            update = {},
            onRelease = {}, // never destroy or detach the shared WKWebView; IosReadingWebViews owns it
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
        )
    }
}

/** Invisible semantics value whose change after a re-attach refreshes the pane's accessibility element (see above). */
private val PaneAttachGeneration = SemanticsPropertyKey<Int>("PaneAttachGeneration")
