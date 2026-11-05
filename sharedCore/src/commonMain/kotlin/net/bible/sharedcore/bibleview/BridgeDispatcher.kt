package net.bible.sharedcore.bibleview

/**
 * Routes BibleView bridge calls (method name + JSON args) to [bridge] through [BibleBridgeDecoder]
 * (L2a spec §3.2, §4.3). Android's `androidBridge.postMessage` and iOS's `WKScriptMessageHandler`
 * both feed it. Never throws: callers sit on an interop boundary. A deferred call that fails
 * (bad arguments or a throwing handler) is answered with [respondNull] so the JS promise settles.
 */
class BridgeDispatcher(
    private val bridge: BibleBridge,
    private val respondNull: (callId: Long) -> Unit,
    private val log: (String) -> Unit,
) {
    fun dispatch(raw: String): BridgeResult =
        parseBridgeMessage(raw).fold({ dispatch(it) }) { e -> error("malformed bridge message: ${e.message}") }

    fun dispatch(message: BridgeMessage): BridgeResult = try {
        if (isStale(message)) {
            safeLog("bridge: dropped ${message.method} of a replaced page (generation ${message.documentGeneration})")
            if (message.method in BibleBridgeDecoder.deferredMethods) answerNull(message)
            BridgeResult.Dropped(message.method)
        } else if (BibleBridgeDecoder.decode(message.method, message.args, bridge)) {
            BridgeResult.Handled
        } else {
            safeLog("bridge: unknown method ${message.method}")
            BridgeResult.Unhandled(message.method)
        }
    } catch (e: Throwable) {
        if (message.method in BibleBridgeDecoder.deferredMethods) answerNull(message)
        error("${message.method} failed: ${e.message}")
    }

    /**
     * A [BridgeGenerationPolicy.guarded] call from a page whose document is not the one the bridge admitted: the page's
     * previous document, or the old page of a web view handed to another window (#3950, osTicket 3508, #3975).
     */
    private fun isStale(message: BridgeMessage): Boolean {
        if (message.method !in BridgeGenerationPolicy.guarded) return false
        val gate = bridge as? DocumentGenerationGate ?: return false
        val admitted = gate.admittedDocumentGeneration
        return admitted == NO_DOCUMENT_GENERATION || message.documentGeneration != admitted
    }

    private fun answerNull(message: BridgeMessage) {
        val callId = BibleBridgeDecoder.callIdOf(message.args) ?: return
        runCatching { respondNull(callId) }
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
