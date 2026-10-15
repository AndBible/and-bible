package net.bible.sharedcore.bibleview

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeHost : JsHost {
    override var isMainThread = true
    override var isAttached = true
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    val posted = mutableListOf<() -> Unit>()
    val evaluated = mutableListOf<String>()
    var result = "\"r\""
    override fun post(block: () -> Unit) { posted += block }
    override fun evaluate(js: String, callback: ((String) -> Unit)?) { evaluated += js; callback?.invoke(result) }
    fun runPosted() { val p = posted.toList(); posted.clear(); p.forEach { it() } }
}

class BibleViewJsChannelTest {
    private val host = FakeHost()
    private val logged = mutableListOf<String>()
    private val channel = BibleViewJsChannel(host) { logged += it }
    private fun ready() = channel.runOnMain { channel.markClientReady() }

    @Test fun dropsWhileLoading() {
        assertTrue(channel.loading)
        assertFalse(channel.execute(JsCall("a")))
        channel.executeNow(JsCall("b"))
        assertTrue(host.evaluated.isEmpty())
        assertTrue(logged.any { "not yet ready" in it })
    }

    @Test fun runsImmediatelyOnMainWhenAttachedAndQueueEmpty() {
        ready()
        assertTrue(channel.execute(JsCall("a")))
        assertEquals(listOf("a;"), host.evaluated)
        assertTrue(host.posted.isEmpty())
    }

    @Test fun offMainCallsQueueAndOnePostFlushesThemInOrder() {
        ready()
        host.isMainThread = false
        channel.execute(JsCall("a"))
        channel.execute(JsCall("b"))
        assertTrue(host.evaluated.isEmpty())
        assertEquals(1, host.posted.size)
        host.runPosted()
        assertEquals(listOf("a;", "b;"), host.evaluated)
    }

    @Test fun mainThreadCallWaitsBehindQueuedTasks() {
        ready()
        host.isMainThread = false
        channel.execute(JsCall("a"))
        host.isMainThread = true
        channel.execute(JsCall("b"))
        assertTrue(host.evaluated.isEmpty())
        host.runPosted()
        assertEquals(listOf("a;", "b;"), host.evaluated)
    }

    @Test fun detachedQueuesWithoutPostingAndFlushesOnAttach() {
        ready()
        host.isAttached = false
        channel.execute(JsCall("a"))
        assertTrue(host.posted.isEmpty())
        assertTrue(host.evaluated.isEmpty())
        host.isAttached = true
        channel.flush()
        assertEquals(listOf("a;"), host.evaluated)
    }

    @Test fun readinessQueuedBeforeAttachRunsOnFlush() {
        host.isAttached = false
        ready()
        assertTrue(channel.loading)
        host.isAttached = true
        channel.flush()
        assertFalse(channel.loading)
    }

    @Test fun executeNowIsGatedButNotQueued() {
        ready()
        host.isMainThread = false
        channel.executeNow(JsCall("x")) { }
        assertEquals(listOf("x;"), host.evaluated)
    }

    @Test fun evaluateReturnsTheCallbackValueAndIsNotGated() = runTest {
        host.result = "\"sel\""
        assertEquals("\"sel\"", channel.evaluate("bibleView.querySelection()"))
        assertEquals(listOf("bibleView.querySelection()"), host.evaluated)
    }
}
