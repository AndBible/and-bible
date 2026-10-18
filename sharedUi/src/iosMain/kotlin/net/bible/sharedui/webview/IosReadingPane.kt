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
import platform.WebKit.WKWebView

/** One reading window's WebView plus the objects WebKit only holds weakly (spec §5 rule 2). */
internal class ReadingEntry(val webView: WKWebView, val navDelegate: IosNavDelegate) {
    var handler: IosBridgeMessageHandler? = null
}

/**
 * Process-wide WKWebView cache for real reading windows, keyed by window id (same rules as the PoC's
 * holder: one WKWebView per window, re-parented, never recreated). Separate from the PoC's cache.
 *
 * The WKWebView exists before the session: build the session's `JsHost` from [webViewFor], then the
 * pane factory ([iosReadingPaneFactory]) wires the bridge handler once it can ask `sessionFor`.
 */
@OptIn(ExperimentalForeignApi::class)
object IosReadingWebViews {
    private val entries = mutableMapOf<String, ReadingEntry>()

    /** The window's WKWebView, created (without bridge handler, not yet loaded) on first use. */
    fun webViewFor(windowId: String): WKWebView = entry(windowId).webView

    /** Drops the window's WebView for good (window closed): unregisters the handler. */
    fun release(windowId: String) {
        val entry = entries.remove(windowId) ?: return
        entry.webView.configuration.userContentController.removeScriptMessageHandlerForName("bridge")
        entry.handler = null
    }

    internal fun entry(windowId: String): ReadingEntry =
        entries.getOrPut(windowId) {
            val navDelegate = IosNavDelegate(windowId)
            ReadingEntry(createBibleWkWebView(windowId, navDelegate, null), navDelegate)
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
    if (entry.handler == null) IosReadingWebViews.attachSession(windowId, sessionFor(windowId))
    UIKitView(
        // Detach from any previous (disposed) interop holder before re-parenting the cached view.
        factory = { entry.webView.apply { removeFromSuperview() } },
        modifier = modifier.testTag("pane-$windowId"),
        update = {},
        onRelease = {}, // never destroy or detach the shared WKWebView; IosReadingWebViews owns it
        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
    )
}
