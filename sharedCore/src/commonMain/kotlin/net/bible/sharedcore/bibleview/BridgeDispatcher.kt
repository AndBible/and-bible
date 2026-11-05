package net.bible.sharedcore.bibleview

/**
 * Routes BibleView bridge calls (method name + JSON args) to [bridge] through [BibleBridgeDecoder]
 * (L2a spec §3.2, §4.3). Android's `androidBridge.postMessage` and iOS's `WKScriptMessageHandler`
 * both feed it. Never throws: callers sit on an interop boundary. A deferred call that fails
 * (bad arguments or a throwing handler) is answered with [respondNull] so the JS promise settles.
 *
 * A [BridgeGenerationPolicy.guarded] call goes through [gate] (#3975): dropped at once when its document generation is
 * not admitted, otherwise run by the gate where window state lives, which checks the generation again there. The
 * default gate is the bridge itself, or [ClosedDocumentGate] (guarded calls always dropped) when it has none.
 */
class BridgeDispatcher(
    private val bridge: BibleBridge,
    private val respondNull: (callId: Long) -> Unit,
    private val log: (String) -> Unit,
    private val gate: DocumentGenerationGate = bridge.documentGenerationGate(),
) {
    fun dispatch(raw: String): BridgeResult =
        parseBridgeMessage(raw).fold({ dispatch(it) }) { e -> error("malformed bridge message: ${e.message}") }

    fun dispatch(message: BridgeMessage): BridgeResult = try {
        if (message.method in BridgeGenerationPolicy.guarded) {
            if (!gate.isAdmitted(message.documentGeneration)) {
                drop(message)
                BridgeResult.Dropped(message.method)
            } else {
                gate.runAdmitted(message.documentGeneration, call = { execute(message) }, drop = { drop(message) })
                BridgeResult.Handled
            }
        } else {
            execute(message)
        }
    } catch (e: Throwable) {
        if (message.method in BibleBridgeDecoder.deferredMethods) answerNull(message)
        error("${message.method} failed: ${e.message}")
    }

    private fun execute(message: BridgeMessage): BridgeResult = try {
        if (BibleBridgeDecoder.decode(message.method, message.args, bridge)) {
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
     * A guarded call from a page whose document is not the one the window admitted (or no longer is when the call
     * would run): the page's previous document, or the old page of a web view handed to another window (#3950,
     * osTicket 3508, #3975). A deferred one is answered with null so the page's promise settles.
     */
    private fun drop(message: BridgeMessage) {
        safeLog("bridge: dropped ${message.method} of a replaced page (generation ${message.documentGeneration})")
        if (message.method in BibleBridgeDecoder.deferredMethods) answerNull(message)
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
