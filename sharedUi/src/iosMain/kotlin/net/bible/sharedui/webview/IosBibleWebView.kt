package net.bible.sharedui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.platform.testTag
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedui.poc.LocalPocPaneCreated
import net.bible.sharedui.poc.PocBibleViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.setValue
import platform.Foundation.stringWithContentsOfFile
import platform.UIKit.UIAccessibilityIdentificationProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val BUNDLE_DIR = "bibleview-js"

/** Reloads the page when WebKit kills the content process; the marker lets CI count terminations. */
private class IosNavDelegate(private val windowId: String) : NSObject(), WKNavigationDelegateProtocol {
    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        try {
            NSLog("I0-WEBVIEW-TERMINATED %@", windowId)
            webView.reload()
        } catch (t: Throwable) {
            NSLog("bridge[%@]: terminate handler failure %@", windowId, t.toString())
        }
    }
}

/** One window's WebView plus the objects WebKit only holds weakly (spec §5 rule 2). */
private class PaneEntry(val webView: WKWebView, val handler: IosBridgeMessageHandler, val navDelegate: IosNavDelegate)

/**
 * Process-wide WebView cache keyed by windowId (spec §5 rule 4): moving a pane or changing the split
 * re-parents the same WKWebView instead of recreating it. The PoC keeps windows 1-3 for the whole
 * process, so [release] is never called; it exists for the day a window is closed for good.
 */
@OptIn(ExperimentalForeignApi::class)
private object IosWebViewHolder {
    private val entries = mutableMapOf<String, PaneEntry>()

    fun obtain(controller: PocBibleViewController): PaneEntry =
        entries.getOrPut(controller.windowId) { create(controller) }

    fun release(windowId: String) {
        val entry = entries.remove(windowId) ?: return
        entry.webView.configuration.userContentController.removeScriptMessageHandlerForName("bridge")
    }

    private fun bundleScript(name: String): String =
        NSBundle.mainBundle.pathForResource(name, "js", BUNDLE_DIR)
            ?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) } ?: ""

    private fun create(controller: PocBibleViewController): PaneEntry {
        val windowId = controller.windowId
        lateinit var webView: WKWebView
        val sink = JsSink { js ->
            dispatch_async(dispatch_get_main_queue()) {
                try {
                    webView.evaluateJavaScript(js, null)
                } catch (t: Throwable) {
                    NSLog("bridge[%@]: evaluateJavaScript failure %@", windowId, t.toString())
                }
            }
        }
        val handlers = controller.handlers + ("console" to BridgeHandler { args, _ ->
            NSLog("js[%@]: %@", windowId, args.joinToString(" "))
        })
        val dispatcher = BridgeDispatcher(sink, { msg -> NSLog("bridge[%@]: %@", windowId, msg) }, handlers)
        val handler = IosBridgeMessageHandler(dispatcher)
        val navDelegate = IosNavDelegate(windowId)

        val config = WKWebViewConfiguration()
        config.preferences.setValue(true, forKey = "allowFileAccessFromFileURLs")
        config.userContentController.apply {
            addUserScript(
                WKUserScript(
                    source = bundleScript("ios-shim"),
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
            addUserScript(
                WKUserScript(
                    source = "window.__activeLanguages__ = '[\"en\"]';",
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
            addScriptMessageHandler(handler, "bridge")
        }
        webView = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
        webView.navigationDelegate = navDelegate
        // XCUITest sees several WebView-type elements per WKWebView (I0 CI: 3 per pane); the UI tests count
        // panes by this identifier instead.
        (webView as UIAccessibilityIdentificationProtocol).setAccessibilityIdentifier("bible-webview-$windowId")
        webView.inspectable = true   // iOS 16.4+: lets Safari Web Inspector attach to this WKWebView
        load(webView, controller.darkTheme)
        return PaneEntry(webView, handler, navDelegate)
    }

    private fun load(webView: WKWebView, dark: Boolean) {
        val folder = NSBundle.mainBundle.resourceURL?.URLByAppendingPathComponent(BUNDLE_DIR, isDirectory = true)
        val index = folder?.URLByAppendingPathComponent("index.html")
        if (folder == null || index == null) {
            NSLog("bridge: bibleview-js bundle folder missing")
            return
        }
        val components = NSURLComponents(uRL = index, resolvingAgainstBaseURL = false)
        components.queryItems = listOf(
            NSURLQueryItem(name = "lang", value = "en"),
            NSURLQueryItem(name = "night", value = dark.toString()),
        )
        val url: NSURL = components.URL ?: index
        webView.loadFileURL(url, allowingReadAccessToURL = folder)
    }
}

/** iOS pane: the cached WKWebView hosted in a Compose `UIKitView`. */
@OptIn(ExperimentalForeignApi::class)
val IosBibleWebViewFactory: BibleWebViewFactory = { controller, modifier ->
    val entry = IosWebViewHolder.obtain(controller)
    val onCreated = LocalPocPaneCreated.current
    DisposableEffect(controller.windowId) {
        onCreated(controller.windowId)
        onDispose {}
    }
    UIKitView(
        // Detach from any previous (disposed) interop holder before re-parenting the cached view.
        // Survival across re-parenting must be confirmed on the simulator in CI (Task 8/9): split 1->2->3->1
        // and reading -> bookmarks -> back, with no I0-WEBVIEW-TERMINATED.
        factory = { entry.webView.apply { removeFromSuperview() } },
        modifier = modifier.testTag("pane-${controller.windowId}"),
        update = {},
        onRelease = {}, // never destroy or detach the shared WKWebView; the holder owns it
        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
    )
}
