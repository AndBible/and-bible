package net.bible.sharedcore.event

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EventSourceTest {

    @Test fun `subscribe delivers synchronously before emit returns`() {
        val source = EventSource<Int>()
        val seen = mutableListOf<Int>()
        source.subscribe { seen += it }
        source.emit(7)
        assertEquals(listOf(7), seen)
    }

    @Test fun `handlers run in subscription order`() {
        val source = EventSource<Int>()
        val seen = mutableListOf<String>()
        source.subscribe { seen += "a$it" }
        source.subscribe { seen += "b$it" }
        source.emit(1)
        assertEquals(listOf("a1", "b1"), seen)
    }

    @Test fun `cancel stops later deliveries and is idempotent`() {
        val source = EventSource<Int>()
        val seen = mutableListOf<Int>()
        val s = source.subscribe { seen += it }
        source.emit(1)
        s.cancel()
        s.cancel()
        source.emit(2)
        assertEquals(listOf(1), seen)
    }

    @Test fun `cancel during dispatch does not affect the current snapshot`() {
        val source = EventSource<Int>()
        val seen = mutableListOf<String>()
        lateinit var b: Subscription
        source.subscribe { seen += "a$it"; b.cancel() }
        b = source.subscribe { seen += "b$it" }
        source.emit(1)
        source.emit(2)
        assertEquals(listOf("a1", "b1", "a2"), seen)
    }

    @Test fun `re-entrant emit from a handler is delivered depth-first like the bus`() {
        val source = EventSource<Int>()
        val seen = mutableListOf<String>()
        source.subscribe { seen += "a$it"; if (it == 1) source.emit(2) }
        source.subscribe { seen += "b$it" }
        source.emit(1)
        assertEquals(listOf("a1", "a2", "b2", "b1"), seen)
    }

    @Test fun `a throwing handler does not stop the others or the flow`() = runTest {
        val source = EventSource<Int>()
        val seen = mutableListOf<String>()
        val flowSeen = mutableListOf<Int>()
        val job = launch { source.asFlow().collect { flowSeen += it } }
        runCurrent()
        source.subscribe { throw IllegalStateException("boom") }
        source.subscribe { seen += "b$it" }
        source.emit(1)
        advanceUntilIdle()
        assertEquals(listOf("b1"), seen)
        assertEquals(listOf(1), flowSeen)
        job.cancel()
    }

    @Test fun `subscribeOnMain defers to the main dispatcher`() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val source = EventSource<Int>()
            val seen = mutableListOf<Int>()
            source.subscribeOnMain { seen += it }
            source.emit(1)
            assertEquals(emptyList<Int>(), seen)
            main.scheduler.advanceUntilIdle()
            assertEquals(listOf(1), seen)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // A slow collector must not lose a burst: callbackFlow's default buffer (64) would drop here.
    @Test fun `asFlow loses nothing across a 1000-emit burst`() = runTest {
        val source = EventSource<Int>()
        val received = mutableListOf<Int>()
        val job = launch { source.asFlow().collect { received += it } }
        runCurrent()                       // subscription registered, collector parked
        repeat(1000) { source.emit(it) }   // no suspension between emits
        advanceUntilIdle()
        assertEquals((0 until 1000).toList(), received)
        job.cancel()
    }

    @Test fun `cancelling the flow collector unsubscribes`() = runTest {
        val source = EventSource<Int>()
        val received = mutableListOf<Int>()
        val job = launch { source.asFlow().collect { received += it } }
        runCurrent()
        source.emit(1)
        runCurrent()
        job.cancel()
        runCurrent()
        source.emit(2)
        runCurrent()
        assertEquals(listOf(1), received)
    }

    @Test fun `Subscriptions cancelAll cancels every member and can be reused`() {
        val source = EventSource<Int>()
        val seen = mutableListOf<String>()
        val bag = Subscriptions()
        bag.add(source.subscribe { seen += "a$it" })
        bag.add(source.subscribe { seen += "b$it" })
        bag.cancelAll()
        source.emit(1)
        bag.add(source.subscribe { seen += "c$it" })
        source.emit(2)
        assertEquals(listOf("c2"), seen)
    }
}
