package net.bible.sharedui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.platform.testTag
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.poc.LocalPocPaneCreated
import net.bible.sharedui.poc.PocBibleViewController
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLRequest
import platform.Foundation.setValue
import net.bible.sharedui.host.reading.HostReadingWindows
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val BUNDLE_DIR = "bibleview-js"

/** Whether [url] is at [BIBLE_ORIGIN] exactly (path, query or fragment may follow; `bibleview://app.evil` is not). */
internal fun isAtBibleOrigin(url: String?): Boolean {
    if (url == null || !url.startsWith(BIBLE_ORIGIN)) return false
    return url.length == BIBLE_ORIGIN.length || url[BIBLE_ORIGIN.length] in "/?#"
}

/**
 * The link a navigation stands for, or null when it is none: a main-frame navigation of the bundle page
 * ([documentUrl] at [BIBLE_ORIGIN]) to a [target] away from the bundle origin is a link click (BibleView-JS opens every
 * link, `osis://`, `ab-w://`, `multi://`, `https://`..., with `window.location.assign`). A bundle load, a subframe
 * navigation or a navigation of a foreign document is not.
 */
internal fun iosLinkNavigation(target: String?, isMainFrame: Boolean, documentUrl: String?): String? = when {
    target.isNullOrEmpty() || isAtBibleOrigin(target) -> null
    !isMainFrame || !isAtBibleOrigin(documentUrl) -> null
    else -> target
}

/**
 * Opens the page's link clicks and reloads the page when WebKit kills the content process (macOS and iOS do that to a
 * hidden app, e.g. AndBible left on another virtual desktop); the marker lets CI count terminations. After the reload
 * [onPageRestart] loads the window's text at its current position; the restarted page gets its document through
 * `DocumentLoader.setClientReady` either way. A link navigation ([iosLinkNavigation]) is cancelled and handed to [openLink] (Android's
 * `shouldOverrideUrlLoading`): WebKit has no handler for `osis://` and the like, so without this a click did nothing.
 */
internal class IosNavDelegate(
    private val windowId: String,
    private val openLink: (String) -> Boolean = { url -> HostReadingWindows.openLink(windowId, url) },
    private val onPageRestart: () -> Unit = { HostReadingWindows.pageRestarted(windowId) },
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val link = try {
            iosLinkNavigation(
                target = decidePolicyForNavigationAction.request.URL?.absoluteString,
                isMainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame == true,
                documentUrl = webView.URL?.absoluteString,
            )
        } catch (t: Throwable) {
            nsLog("bridge[$windowId]: navigation policy failure $t")
            null
        }
        if (link == null) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        try {
            if (!openLink(link)) nsLog("bridge[$windowId]: unhandled link $link")
        } catch (t: Throwable) {
            nsLog("bridge[$windowId]: link handler failure for $link: $t")
        }
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        try {
            nsLog("I0-WEBVIEW-TERMINATED $windowId")
            webView.reload()
            onPageRestart()
        } catch (t: Throwable) {
            nsLog("bridge[$windowId]: terminate handler failure $t")
        }
    }
}

/**
 * Creates the WKWebView every Bible pane uses (PoC and real reading pane share it). [handler] is added
 * as the "bridge" message handler when given; the real reading pane passes null and adds it once its
 * session exists (see [IosReadingWebViews]). The caller must hold [handler] and [navDelegate] strongly.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal fun createBibleWkWebView(
    windowId: String,
    navDelegate: IosNavDelegate,
    handler: IosBridgeMessageHandler?,
    /** `window.__activeLanguages__` as a JSON list, read when the WebView is created (the real panes pass the installed Bibles' languages). */
    activeLanguages: () -> String = { "[\"en\"]" },
    newWebView: (WKWebViewConfiguration) -> WKWebView = { WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = it) },
): WKWebView {
    val config = WKWebViewConfiguration()
    // The page and its add-on assets come from bibleview://app (#3918); the configuration keeps the handler.
    config.setURLSchemeHandler(IosBibleSchemeHandler(bibleBundleDir()), forURLScheme = BIBLE_SCHEME)
    config.userContentController.apply {
        addUserScript(
            WKUserScript(
                source = activeLanguagesScript(activeLanguages()),
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = true,
            ),
        )
        // Debug binaries only: console output and page errors carry Bible text and notes, so release builds neither
        // inject the forwarder nor register its handler.
        if (Platform.isDebugBinary) {
            addUserScript(
                WKUserScript(
                    source = CONSOLE_USER_SCRIPT,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
            addScriptMessageHandler(sharedConsoleHandler, CONSOLE_HANDLER_NAME)
        }
        if (handler != null) addScriptMessageHandler(handler, "bridge")
    }
    val webView = newWebView(config)
    webView.navigationDelegate = navDelegate
    // XCUITest sees several WebView-type elements per WKWebView (I0 CI: 3 per pane); the UI tests count
    // panes by this identifier instead. Set through KVC: Kotlin does not see WKWebView as a
    // UIAccessibilityIdentificationProtocol, so `as UIAccessibilityIdentificationProtocol` throws
    // TypeCastException (I0 CI run 38032574023) and the property is not exposed on UIView.
    webView.setValue("bible-webview-$windowId", forKey = "accessibilityIdentifier")
    webView.inspectable = true   // iOS 16.4+: lets Safari Web Inspector attach to this WKWebView
    return webView
}

/** The BibleView page URL for [index]: `index.html?lang=..&night=..`. */
internal fun bibleBundleUrl(index: NSURL, lang: String, dark: Boolean): NSURL {
    val components = NSURLComponents(uRL = index, resolvingAgainstBaseURL = false)
    components.queryItems = listOf(
        NSURLQueryItem(name = "lang", value = lang),
        NSURLQueryItem(name = "night", value = dark.toString()),
    )
    return components.URL ?: index
}

/** The app bundle's `bibleview-js` folder, or null (test binaries have none). */
internal fun bibleBundleDir(): Path? = NSBundle.mainBundle.resourcePath?.let { "$it/$BUNDLE_DIR".toPath() }

/** The window's [IosBibleSchemeHandler] (registered by [createBibleWkWebView]), or null for another WebView. */
internal val WKWebView.bibleSchemeHandler: IosBibleSchemeHandler?
    get() = configuration.urlSchemeHandlerForURLScheme(BIBLE_SCHEME) as? IosBibleSchemeHandler

/**
 * Loads the bundled BibleView page (`bibleview://app/index.html?lang=..&night=..`) into [webView]. The page is served
 * by [IosBibleSchemeHandler], not from `file://`, so its root-relative add-on asset URLs reach the page asset routes.
 */
internal fun loadBibleBundle(webView: WKWebView, lang: String, dark: Boolean) {
    val index = NSURL.URLWithString("$BIBLE_ORIGIN/index.html") ?: return
    webView.loadRequest(NSURLRequest.requestWithURL(bibleBundleUrl(index, lang, dark)))
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
        removeConsoleHandler(entry.webView.configuration.userContentController)
    }

    private fun create(controller: PocBibleViewController): PaneEntry {
        val windowId = controller.windowId
        lateinit var webView: WKWebView
        val sink: (JsCall) -> Unit = { call ->
            dispatch_async(dispatch_get_main_queue()) {
                try {
                    webView.evaluateJavaScript(call.js) { _, error ->
                        if (error != null) nsLog("bridge[$windowId]: evaluateJavaScript error ${error.localizedDescription}")
                    }
                } catch (t: Throwable) {
                    nsLog("bridge[$windowId]: evaluateJavaScript failure $t")
                }
            }
        }
        val log: (String) -> Unit = { msg -> nsLog("bridge[$windowId]: $msg") }
        val bridge = controller.bridge(sink, log)
        val dispatcher = BridgeDispatcher(bridge, { callId -> sink(BibleViewEvents.response(callId, null)) }, log)
        val handler = IosBridgeMessageHandler(dispatcher)
        val navDelegate = IosNavDelegate(windowId)

        webView = createBibleWkWebView(windowId, navDelegate, handler)
        loadBibleBundle(webView, "en", controller.darkTheme)
        return PaneEntry(webView, handler, navDelegate)
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
        modifier = modifier.testTag("pane-${controller.windowId}").wheelScrollsWebView(controller.windowId, entry.webView),
        update = {},
        onRelease = {}, // never destroy or detach the shared WKWebView; the holder owns it
        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
    )
}
