package net.bible.sharedui.webview

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSLog
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
            val data = NSJSONSerialization.dataWithJSONObject(didReceiveScriptMessage.body, 0u, null) ?: return
            val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return
            dispatcher.dispatch(text)
        } catch (t: Throwable) {
            NSLog("bridge: handler failure %@", t.toString())
        }
    }
}
