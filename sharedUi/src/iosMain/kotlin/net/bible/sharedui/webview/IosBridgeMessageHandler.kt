package net.bible.sharedui.webview

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.log.nsLog
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

/**
 * Receives `webkit.messageHandlers.bridge.postMessage(...)` and hands the JSON text to [dispatcher].
 * WebKit delivers on the main thread. Must be held strongly by its pane (spec §5 rule 2) and removed
 * with `removeScriptMessageHandlerForName("bridge")` on dispose (rule 3). Never throws (rule 5).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBridgeMessageHandler(private val dispatcher: BridgeDispatcher) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        try {
            // dataWithJSONObject raises an ObjC exception (fatal in K/N) on invalid input, so guard first.
            if (!NSJSONSerialization.isValidJSONObject(didReceiveScriptMessage.body)) {
                nsLog("bridge: invalid JSON body")
                return
            }
            val data = NSJSONSerialization.dataWithJSONObject(didReceiveScriptMessage.body, 0u, null) ?: return
            val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return
            dispatcher.dispatch(text)
        } catch (t: Throwable) {
            nsLog("bridge: handler failure $t")
        }
    }
}

/**
 * Forwards the page's console output and uncaught errors to the app log (`js[level] text`), through the
 * [CONSOLE_USER_SCRIPT] posting to the `"console"` handler. Stateless, so one instance serves every WebView; it is
 * held here because WebKit holds script-message handlers weakly. Registered by `createBibleWkWebView` and removed
 * with `"bridge"` when the WebView is released ([removeConsoleHandler]).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosConsoleMessageHandler : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        try {
            val text = didReceiveScriptMessage.body as? String ?: return
            val level = text.substringBefore('|', "log")
            nsLog("js[$level] ${text.substringAfter('|')}")
        } catch (t: Throwable) {
            nsLog("bridge: console handler failure $t")
        }
    }
}

/** The one shared console handler (top-level: an ObjC subclass cannot have a companion field). */
internal val sharedConsoleHandler = IosConsoleMessageHandler()

internal const val CONSOLE_HANDLER_NAME = "console"

internal fun removeConsoleHandler(controller: WKUserContentController) {
    controller.removeScriptMessageHandlerForName(CONSOLE_HANDLER_NAME)
}

/** Wraps console.* and reports window errors and unhandled rejections to the `"console"` handler as `level|text`. */
internal const val CONSOLE_USER_SCRIPT = """
(function () {
  if (window.__abConsole) return; window.__abConsole = true;
  function send(level, args) {
    try {
      var t = Array.prototype.map.call(args, function (x) {
        try { return typeof x === 'string' ? x : (x instanceof Error ? (x.stack || x.message) : JSON.stringify(x)); }
        catch (e) { return String(x); }
      }).join(' ');
      window.webkit.messageHandlers.console.postMessage(level + '|' + t.slice(0, 2000));
    } catch (e) {}
  }
  ['log', 'info', 'debug', 'warn', 'error'].forEach(function (l) {
    var o = console[l];
    console[l] = function () { send(l, arguments); if (o) o.apply(console, arguments); };
  });
  window.addEventListener('error', function (e) { send('onerror', [e.message + ' @' + e.filename + ':' + e.lineno + ':' + e.colno]); });
  window.addEventListener('unhandledrejection', function (e) { send('unhandledrejection', [(e.reason && e.reason.stack) || String(e.reason)]); });
})();
"""
