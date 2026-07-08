package net.bible.android.control.event

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Base + subtype to prove supertype/interface dispatch is preserved.
open class BaseTestEvent
class SubTestEvent(val payload: Int) : BaseTestEvent()
interface MarkerTestEvent
class MarkerImplEvent : MarkerTestEvent

class ABEventBusTest {
    @AfterTest fun tearDown() = ABEventBus.unregisterAll()

    @Test fun `on delivers synchronously on the posting thread before post returns`() {
        val owner = Any()
        val seen = mutableListOf<Int>()
        ABEventBus.register(owner) { on<SubTestEvent> { seen.add(it.payload) } }
        ABEventBus.post(SubTestEvent(7))
        // Synchronous: the handler already ran by the time post() returned.
        assertEquals(listOf(7), seen)
    }

    @Test fun `handler for a supertype receives posted subtype`() {
        val owner = Any()
        var got = false
        ABEventBus.register(owner) { on<BaseTestEvent> { got = true } }
        ABEventBus.post(SubTestEvent(1))
        assertTrue(got)
    }

    @Test fun `handler for an interface receives posted implementor`() {
        val owner = Any()
        var got = false
        ABEventBus.register(owner) { on<MarkerTestEvent> { got = true } }
        ABEventBus.post(MarkerImplEvent())
        assertTrue(got)
    }

    @Test fun `unregister removes only that owner's handlers`() {
        val a = Any(); val b = Any()
        val seen = mutableListOf<String>()
        ABEventBus.register(a) { on<SubTestEvent> { seen.add("a") } }
        ABEventBus.register(b) { on<SubTestEvent> { seen.add("b") } }
        ABEventBus.unregister(a)
        ABEventBus.post(SubTestEvent(0))
        assertEquals(listOf("b"), seen)
    }

    @Test fun `safelyRegister is idempotent for the same owner`() {
        val owner = Any()
        val seen = mutableListOf<Int>()
        val block: ABEventBus.Subscriptions.() -> Unit = { on<SubTestEvent> { seen.add(it.payload) } }
        ABEventBus.safelyRegister(owner, block)
        ABEventBus.safelyRegister(owner, block)
        ABEventBus.post(SubTestEvent(5))
        assertEquals(listOf(5), seen) // delivered once, not twice
    }

    @Test fun `re-entrant post from inside a handler does not crash and both fire`() {
        val owner = Any()
        val seen = mutableListOf<String>()
        ABEventBus.register(owner) {
            on<SubTestEvent> { seen.add("sub"); ABEventBus.post(MarkerImplEvent()) }
            on<MarkerTestEvent> { seen.add("marker") }
        }
        ABEventBus.post(SubTestEvent(1))
        assertEquals(listOf("sub", "marker"), seen)
    }

    @Test fun `events flow emits posted events`() = runTest {
        val seen = mutableListOf<Any>()
        val job = ABEventBus.events.onEach { seen.add(it) }.launchIn(this)
        // give the collector a turn to subscribe, then post
        kotlinx.coroutines.yield()
        ABEventBus.post(SubTestEvent(9))
        kotlinx.coroutines.yield()
        job.cancel()
        assertTrue(seen.any { it is SubTestEvent && it.payload == 9 })
    }

    @Test fun `a throwing on handler is isolated - later handlers and the flow still run`() = runTest {
        val throwingOwner = Any(); val laterOwner = Any()
        val seen = mutableListOf<String>()
        val flowSeen = mutableListOf<Any>()
        // registration order = delivery order (LinkedHashMap): throwing first, then the good one
        ABEventBus.register(throwingOwner) { on<SubTestEvent> { throw RuntimeException("boom") } }
        ABEventBus.register(laterOwner) { on<SubTestEvent> { seen.add("later") } }
        val job = ABEventBus.events.onEach { flowSeen.add(it) }.launchIn(this)
        kotlinx.coroutines.yield()
        ABEventBus.post(SubTestEvent(1)) // must NOT propagate the handler's exception
        kotlinx.coroutines.yield()
        job.cancel()
        assertEquals(listOf("later"), seen)                 // later handler still ran
        assertTrue(flowSeen.any { it is SubTestEvent })      // tryEmit still happened
    }
}
