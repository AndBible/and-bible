package net.bible.sharedcore.bibleview

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What [BibleViewJsChannel] needs from the platform WebView. [evaluate] is called on the main thread. */
interface JsHost {
    val isMainThread: Boolean
    val isAttached: Boolean
    val mainDispatcher: CoroutineDispatcher
    fun post(block: () -> Unit)
    fun evaluate(js: String, callback: ((String) -> Unit)?)
}

/**
 * The native-to-JS timing that used to live in BibleView: calls are dropped while loading, queued
 * in order when off-main or detached, and otherwise evaluated immediately. Queue operations and
 * task execution remain under one lock to preserve the existing ordering behavior.
 */
class BibleViewJsChannel(private val host: JsHost, private val log: (String) -> Unit) {
    @Volatile
    var loading: Boolean = true
        private set

    private val lock = SynchronizedObject()
    private val queue = ArrayDeque<() -> Unit>()

    /** Queues or runs [call]; false when dropped because the page is still loading. */
    fun execute(call: JsCall): Boolean {
        if (loading) {
            log("HTML not yet ready, js execution is doomed to fail. ${call.js}")
            return false
        }
        runOnMain { executeNow(call) }
        return true
    }

    /** Evaluates [call] now, gated by [loading], bypassing the queue. */
    fun executeNow(call: JsCall, callback: ((String) -> Unit)? = null) {
        if (loading) {
            log("HTML not yet ready, js execution is doomed to fail. ${call.js}")
            return
        }
        host.evaluate("${call.js};", callback)
    }

    fun runOnMain(block: () -> Unit) = synchronized(lock) {
        val wasEmpty = queue.isEmpty()
        val attached = host.isAttached
        if (host.isMainThread && wasEmpty && attached) {
            block()
        } else {
            queue.addLast(block)
            if (wasEmpty && attached) host.post { flush() }
        }
    }

    fun flush() = synchronized(lock) {
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
    }

    /** Called inside [runOnMain] when the page reports readiness. */
    fun markClientReady() {
        loading = false
    }

    /** Evaluates [js] on the main thread and returns its result; not gated (selection queries). */
    suspend fun evaluate(js: String): String {
        val result = CompletableDeferred<String>()
        withContext(host.mainDispatcher) { host.evaluate(js) { result.complete(it) } }
        return result.await()
    }
}
