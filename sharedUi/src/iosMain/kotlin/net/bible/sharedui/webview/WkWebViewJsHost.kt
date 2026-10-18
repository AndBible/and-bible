package net.bible.sharedui.webview

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.log.nsLog
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSThread
import platform.WebKit.WKWebView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [JsHost] over a WKWebView. WebKit APIs must run on the main thread, so [post] and [evaluate]
 * hop to the main queue (the same rule as the PoC holder's sink). [isAttached] is false while the
 * cached WKWebView is not in a window (between panes), which makes the channel queue calls.
 */
@OptIn(ExperimentalForeignApi::class)
class WkWebViewJsHost(private val webView: WKWebView) : JsHost {
    override val isMainThread: Boolean get() = NSThread.isMainThread
    override val isAttached: Boolean get() = webView.window != null
    override val mainDispatcher: CoroutineDispatcher get() = Dispatchers.Main

    override fun post(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            try {
                block()
            } catch (t: Throwable) {
                nsLog("bridge: posted block failure $t")
            }
        }
    }

    override fun evaluate(js: String, callback: ((String) -> Unit)?) {
        val run = {
            try {
                webView.evaluateJavaScript(js) { result, error ->
                    if (error != null) nsLog("bridge: evaluateJavaScript error ${error.localizedDescription}")
                    callback?.invoke(jsonOf(result))
                }
            } catch (t: Throwable) {
                nsLog("bridge: evaluateJavaScript failure $t")
                callback?.invoke("null")
            }
        }
        if (NSThread.isMainThread) run() else dispatch_async(dispatch_get_main_queue()) { run() }
    }

    /** Android's evaluateJavascript hands back JSON text; WebKit hands back an object, so re-encode the simple cases. */
    private fun jsonOf(result: Any?): String = when (result) {
        null -> "null"
        is NSNumber -> result.stringValue
        is NSString -> {
            val s = result.toString()
            buildString {
                append('"')
                for (c in s) when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
                append('"')
            }
        }
        else -> result.toString()
    }
}
