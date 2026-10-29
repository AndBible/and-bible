package net.bible.desktop.webkitgtk

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class EvalBridgeTest {
    private val timeouts = mutableListOf<Runnable>()
    private var delivered = 0
    private val bridge = EvalBridge(
        deliver = { delivered++; it.run() },
        scheduleTimeout = { _, task -> timeouts += task },
    )
    private val got = mutableListOf<String>()

    @Test
    fun deliversResult() {
        val id = bridge.register { got += it }
        assertEquals(1, bridge.pendingCount)
        bridge.complete(id, "{\"a\":1}")
        assertEquals(listOf("{\"a\":1}"), got)
        assertEquals(0, bridge.pendingCount)
        assertEquals(1, delivered)
    }

    @Test
    fun nullJsonBecomesNullText() {
        bridge.complete(bridge.register { got += it }, null)
        assertEquals(listOf("null"), got)
        assertEquals("null", EvalBridge.resultJson(null))
        assertEquals("3", EvalBridge.resultJson("3"))
    }

    @Test
    fun failGivesNull() {
        bridge.fail(bridge.register { got += it })
        assertEquals(listOf("null"), got)
    }

    @Test
    fun timeoutGivesNullAndLateCompleteIsDropped() {
        val id = bridge.register { got += it }
        assertEquals(1, timeouts.size)
        timeouts.single().run()
        assertEquals(listOf("null"), got)
        bridge.complete(id, "5")
        bridge.fail(id)
        assertEquals(listOf("null"), got)
    }

    @Test
    fun completeThenTimeoutFiresOnce() {
        val id = bridge.register { got += it }
        bridge.complete(id, "1")
        timeouts.single().run()
        assertEquals(listOf("1"), got)
    }

    @Test
    fun failAllAnswersEverythingPending() {
        repeat(3) { bridge.register { got += it } }
        bridge.failAll()
        assertEquals(listOf("null", "null", "null"), got)
        assertEquals(0, bridge.pendingCount)
        bridge.failAll()
        assertEquals(3, got.size)
    }

    @Test
    fun unknownIdIgnored() {
        bridge.complete(999, "1")
        bridge.fail(999)
        assertEquals(0, got.size)
    }

    @Test
    fun throwingCallbackDoesNotBreakOthers() {
        bridge.register { error("expected in test") }
        bridge.register { got += it }
        bridge.failAll()
        assertEquals(listOf("null"), got)
    }

    @Test
    fun completeFromOtherThreadDeliveredOnce() {
        val latch = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<String>())
        val id = bridge.register { results += it; latch.countDown() }
        val threads = List(4) { thread { bridge.complete(id, "9") } }
        threads.forEach { it.join() }
        latch.await(5, TimeUnit.SECONDS)
        assertEquals(listOf("9"), results)
    }
}
