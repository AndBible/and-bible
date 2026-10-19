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
}

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

    /** Loads the bundle into a WebView; a test seam (the test binary has no `bibleview-js` bundle). */
    internal var bundleLoader: (WKWebView, String, Boolean) -> Unit = { webView, lang, dark -> loadBibleBundle(webView, lang, dark) }

    /** The window's WKWebView, created (without bridge handler, not yet loaded) on first use. */
    fun webViewFor(windowId: String): WKWebView = entry(windowId).webView

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
        // The closure holds the session (and through it the JS channel); drop it so the WebView does not keep them alive.
        entry.webView.onAttached = null
    }

    internal fun resetForTest() {
        for (id in entries.keys.toList()) release(id)
        bundleLoader = { webView, lang, dark -> loadBibleBundle(webView, lang, dark) }
    }

    private fun checkMain() = check(NSThread.isMainThread) { "IosReadingWebViews is main-thread only" }

    internal fun entry(windowId: String): ReadingEntry {
        checkMain()
        return entries.getOrPut(windowId) {
            val navDelegate = IosNavDelegate(windowId)
            val webView = createBibleWkWebView(windowId, navDelegate, null) {
                ReadingWkWebView(CGRectMake(0.0, 0.0, 0.0, 0.0), it)
            } as ReadingWkWebView
            ReadingEntry(webView, navDelegate)
        }
    }

    /**
     * Adds the bridge handler and loads the bundle, once per window. [dark] is only the attach-time initial value of
     * the `night` URL parameter; live night mode reaches the page through `set_config`.
     */
    internal fun attachSession(windowId: String, session: BibleWindowSession, bridge: BibleBridge, dark: Boolean) {
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
        val lang = NSLocale.currentLocale.languageCode.ifEmpty { "en" }
        bundleLoader(entry.webView, lang, dark)
    }
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
) {
    // A closed or missing window gets no entry, no session and no WebView, whatever the recomposition order.
    if (!isOpen(windowId)) return
    val entry = IosReadingWebViews.entry(windowId)
    if (entry.handler != null || entry.failed) return
    try {
        val session = sessionFor(windowId)
        val bridge = bridgeFor?.invoke(windowId) ?: ReadingBridge(session)
        IosReadingWebViews.attachSession(windowId, session, bridge, nightMode())
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
): ReadingPaneFactory = { windowId, modifier ->
    attachReadingPane(windowId, sessionFor, bridgeFor, nightMode, isOpen)
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
