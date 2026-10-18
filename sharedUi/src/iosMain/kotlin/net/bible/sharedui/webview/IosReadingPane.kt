package net.bible.sharedui.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
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
@OptIn(ExperimentalForeignApi::class)
object IosReadingWebViews {
    private val entries = mutableMapOf<String, ReadingEntry>()

    /** The window's WKWebView, created (without bridge handler, not yet loaded) on first use. */
    fun webViewFor(windowId: String): WKWebView = entry(windowId).webView

    /**
     * Drops the window's WebView for good (window closed): unregisters the handler. The owner must also
     * `dispose()` the window's [BibleWindowSession]; this does not.
     */
    fun release(windowId: String) {
        checkMain()
        val entry = entries.remove(windowId) ?: return
        entry.webView.configuration.userContentController.removeScriptMessageHandlerForName("bridge")
        entry.handler = null
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

    /** Adds the bridge handler and loads the bundle, once per window. */
    internal fun attachSession(windowId: String, session: BibleWindowSession) {
        val entry = entry(windowId)
        if (entry.handler != null) return
        val log: (String) -> Unit = { msg -> nsLog("bridge[$windowId]: $msg") }
        val dispatcher = BridgeDispatcher(
            ReadingBridge(session),
            { callId -> session.emit(BibleViewEvents.response(callId, null)) },
            log,
        )
        val handler = IosBridgeMessageHandler(dispatcher)
        entry.webView.onAttached = { session.jsChannel.flush() }
        entry.handler = handler // strong reference: WebKit holds the handler weakly
        entry.webView.configuration.userContentController.addScriptMessageHandler(handler, "bridge")
        val lang = NSLocale.currentLocale.languageCode.ifEmpty { "en" }
        loadBibleBundle(entry.webView, lang, dark = false)
    }
}

/**
 * iOS [ReadingPaneFactory]: hosts the window's cached WKWebView in a `UIKitView`, wired to the real
 * session returned by [sessionFor] (called once per window, at first composition).
 */
@OptIn(ExperimentalForeignApi::class)
fun iosReadingPaneFactory(sessionFor: (windowId: String) -> BibleWindowSession): ReadingPaneFactory = { windowId, modifier ->
    val entry = IosReadingWebViews.entry(windowId)
    if (entry.handler == null && !entry.failed) {
        try {
            IosReadingWebViews.attachSession(windowId, sessionFor(windowId))
        } catch (t: Throwable) {
            entry.failed = true
            nsLog("bridge[$windowId]: reading pane wiring failed $t")
        }
    }
    UIKitView(
        // Detach from any previous (disposed) interop holder before re-parenting the cached view.
        factory = { entry.webView.apply { removeFromSuperview() } },
        modifier = modifier.testTag("pane-$windowId"),
        update = {},
        onRelease = {}, // never destroy or detach the shared WKWebView; IosReadingWebViews owns it
        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
    )
}
