package net.bible.sharedcore.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.control.event.ABEventBus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class Ping(val n: Int)
private data class Pong(val s: String)

class EventBridgeTest {
    @AfterTest fun cleanup() = ABEventBus.unregisterAll()

    @Test fun `eventsOf filters by type - delivers T, drops non-T`() = runTest {
        val collected = mutableListOf<Int>()
        // Subscribe BEFORE posting: ABEventBus's SharedFlow has replay=0, so a collector
        // only receives events posted after it is actively collecting. launchIn + yield()
        // guarantees the collector has subscribed before we post.
        val job = eventsOf<Ping>().onEach { collected.add(it.n) }.launchIn(this)
        yield()
        ABEventBus.post(Pong("ignored"))   // non-T: must be filtered out
        ABEventBus.post(Ping(7))           // T: must be delivered
        ABEventBus.post(Pong("also-ignored"))
        yield()
        job.cancel()
        assertEquals(listOf(7), collected)
    }

    @Test fun `stateFromEvents folds T events into state`() = runTest {
        // backgroundScope hosts the never-ending fold collector; runTest cancels it at test end
        // (avoids UncompletedCoroutinesError from launching it directly on the test scope).
        val state = stateFromEvents<Ping, Int>(backgroundScope, initial = 0) { acc, ev -> acc + ev.n }
        assertEquals(0, state.value) // starts at initial
        yield() // let the internal collector subscribe
        ABEventBus.post(Pong("ignored")) // non-T does not affect state
        ABEventBus.post(Ping(3))
        ABEventBus.post(Ping(4))
        // Wait for the fold to reach the expected value deterministically.
        val settled = state.first { it == 7 }
        assertEquals(7, settled)
    }
}
