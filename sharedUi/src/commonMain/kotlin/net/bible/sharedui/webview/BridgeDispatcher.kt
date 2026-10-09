package net.bible.sharedui.webview

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Runs JavaScript in the page. Implementations hop to the platform's UI thread themselves. */
fun interface JsSink { fun evaluate(js: String) }

fun interface BridgeHandler { fun handle(args: List<JsonElement>, js: JsSink) }

/**
 * The `window.android` methods BibleView calls through `deferredCall` (`composables/android.ts`):
 * their first argument is a `callId` and JS awaits `bibleView.response(callId, value)`.
 */
val ASYNC_BRIDGE_METHODS: Set<String> = setOf(
    "parseRef", "refChooserDialog", "requestMoreToEnd", "requestMoreToBeginning", "getMyDocumentPageRawContent",
)

fun emitJs(event: String, vararg jsonArgs: String): String =
    (listOf("'$event'") + jsonArgs).joinToString(", ", prefix = "bibleView.emit(", postfix = ")")

/**
 * Routes BibleView bridge calls (method name + JSON args) to handlers. Platform-neutral: the iOS
 * `WKScriptMessageHandler` feeds it today; L2 moves Android's `@JavascriptInterface` methods behind it.
 * Never throws: every failure is a logged [BridgeResult.Error], because callers sit on an interop boundary.
 */
class BridgeDispatcher(
    private val js: JsSink,
    private val log: (String) -> Unit,
    private val handlers: Map<String, BridgeHandler>,
) {
    fun dispatch(raw: String): BridgeResult =
        parseBridgeMessage(raw).fold({ message -> dispatch(message) }) { e -> error("malformed bridge message: ${e.message}") }

    fun dispatch(message: BridgeMessage): BridgeResult {
        val handler = handlers[message.method]
        if (handler != null) {
            return runCatching { handler.handle(message.args, js) }
                .fold({ BridgeResult.Handled }) { e -> error("${message.method} failed: ${e.message}") }
        }
        if (message.method in ASYNC_BRIDGE_METHODS) {
            val callId = (message.args.firstOrNull() as? JsonPrimitive)?.content?.toIntOrNull()
                ?: return error("${message.method} without a numeric callId")
            return try {
                js.evaluate("bibleView.response($callId, null)")
                safeLog("bridge: unhandled ${message.method}")
                BridgeResult.Unhandled(message.method)
            } catch (e: Throwable) {
                error("${message.method} response failed: ${e.message}")
            }
        }
        safeLog("bridge: unhandled ${message.method}")
        return BridgeResult.Unhandled(message.method)
    }

    private fun error(reason: String): BridgeResult.Error {
        safeLog("bridge: $reason")
        return BridgeResult.Error(reason)
    }

    /** Logging must never make [dispatch] throw; a failing logger is dropped silently. */
    private fun safeLog(message: String) {
        runCatching { log(message) }
    }
}
