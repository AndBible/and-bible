@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.ios.reading.IosReadingGestures
import net.bible.sharedui.ios.reading.IosReadingWindows
import net.bible.sharedui.ios.reading.IosSelectionMenu
import net.bible.sharedui.ios.reading.IosSelectionMessageHandler
import net.bible.sharedui.ios.reading.SELECTION_HANDLER_NAME
import net.bible.sharedui.ios.reading.SELECTION_USER_SCRIPT
import net.bible.sharedui.ios.reading.insertSelectionMenu
import platform.UIKit.UIMenuBuilderProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import net.bible.sharedui.reading.bridge.ReadingBridge
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
import platform.UIKit.UIColor
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

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        if (window != null) {
            try {
                onAttached?.invoke()
            } catch (t: Throwable) {
                nsLog("bridge: flush on attach failure $t")
            }
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

    /** The pane's gesture recognisers once attached (see [attachReadingGestures]); detached when the window is released. */
    var gestureRecognisers: IosReadingGestureRecognisers? = null

    /** The page background last applied to the WebView (ARGB), or null before the first `set_config`. */
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
     * WebView entry, or one released before the dispatch runs, is ignored. `opaque` stays as WebKit has it: the
     * colour is opaque in every default mode and shows through.
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
        if (entry.backgroundArgb == argb) return
        entry.backgroundArgb = argb
        val color = uiColorFromArgb(argb)
        entry.webView.backgroundColor = color
        entry.webView.scrollView.backgroundColor = color
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
        removeConsoleHandler(entry.webView.configuration.userContentController)
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
        // The closure holds the session (and through it the JS channel); drop it so the WebView does not keep them alive.
        entry.webView.onAttached = null
    }

    internal fun resetForTest() {
        for (id in entries.keys.toList()) release(id)
        bundleLoader = { webView, lang, dark -> loadBibleBundle(webView, lang, dark) }
        activeLanguages = { "[\"en\"]" }
    }

    private fun checkMain() = check(NSThread.isMainThread) { "IosReadingWebViews is main-thread only" }

    internal fun entry(windowId: String): ReadingEntry {
        checkMain()
        return entries.getOrPut(windowId) {
            val navDelegate = IosNavDelegate(windowId)
            val webView = createBibleWkWebView(windowId, navDelegate, null, activeLanguages) {
                ReadingWkWebView(CGRectMake(0.0, 0.0, 0.0, 0.0), it)
            } as ReadingWkWebView
            ReadingEntry(webView, navDelegate)
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
        selectionMenu: IosSelectionMenu? = null,
    ) {
        val entry = entry(windowId)
        if (entry.handler != null) return
        val log: (String) -> Unit = { msg -> nsLog("bridge[$windowId]: $msg") }
        val dispatcher = BridgeDispatcher(
            bridge,
            { callId -> session.emit(BibleViewEvents.response(callId, null)) },
            log,
        )
        val handler = IosBridgeMessageHandler(dispatcher)
        entry.webView.onAttached = { session.jsChannel.flush() }
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
private fun attachSelectionMenu(entry: ReadingEntry, windowId: String, menu: IosSelectionMenu) {
    if (entry.selectionHandler != null) return
    val handler = IosSelectionMessageHandler(windowId, menu)
    val controller = entry.webView.configuration.userContentController
    controller.addUserScript(
        WKUserScript(
            source = SELECTION_USER_SCRIPT,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            forMainFrameOnly = true,
        ),
    )
    controller.addScriptMessageHandler(handler, SELECTION_HANDLER_NAME)
    entry.selectionHandler = handler
    entry.webView.onBuildMenu = { builder -> insertSelectionMenu(builder, windowId, menu) }
}

/** Attaches [gestures] to the window's WebView once (a second call, or a window without a handler, does nothing). */
internal fun attachReadingGestures(windowId: String, gestures: IosReadingGestures?) {
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
    selectionMenu: IosSelectionMenu? = null,
) {
    // A closed or missing window gets no entry, no session and no WebView, whatever the recomposition order.
    if (!isOpen(windowId)) return
    val entry = IosReadingWebViews.entry(windowId)
    if (entry.handler != null || entry.failed) return
    try {
        val session = sessionFor(windowId)
        val bridge = bridgeFor?.invoke(windowId) ?: ReadingBridge(session)
        IosReadingWebViews.attachSession(windowId, session, bridge, nightMode(), selectionMenu)
    } catch (t: Throwable) {
        entry.failed = true
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
    gesturesFor: (windowId: String) -> IosReadingGestures? = { IosReadingWindows.gesturesFor(it) },
    /** The text-selection menu of the bound host; null (no host bound) leaves WebKit's edit menu untouched. */
    selectionMenu: () -> IosSelectionMenu? = { IosReadingWindows.selectionMenu },
): ReadingPaneFactory = { windowId, modifier ->
    attachReadingPane(windowId, sessionFor, bridgeFor, nightMode, isOpen, selectionMenu())
    if (isOpen(windowId)) attachReadingGestures(windowId, gesturesFor(windowId))
    // ReadingHost composes panes only for windows in the layout; this check covers the gap between the window
    // closing and the asynchronous release, so recomposition never recreates a released window's WebView.
    if (isOpen(windowId)) {
        val entry = IosReadingWebViews.entry(windowId)
        UIKitView(
            // Detach from any previous (disposed) interop holder before re-parenting the cached view.
            factory = { entry.webView.apply { removeFromSuperview() } },
            modifier = modifier.testTag("pane-$windowId"),
            update = {},
            onRelease = {}, // never destroy or detach the shared WKWebView; IosReadingWebViews owns it
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
        )
    }
}
