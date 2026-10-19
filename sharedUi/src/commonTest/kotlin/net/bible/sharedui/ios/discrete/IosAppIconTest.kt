package net.bible.sharedui.ios.discrete

import kotlin.test.Test
import kotlin.test.assertEquals

class IosAppIconTest {
    private class Fixture {
        var current: String? = null
        var supported = true
        var failures = 0
        val calls = mutableListOf<String?>()
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val request = AppIconRequest(
            supportsAlternateIcons = { supported },
            currentName = { current },
            setIcon = { name, complete ->
                calls += name
                if (failures-- > 0) complete(false) else { current = name; complete(true) }
            },
            schedule = { delay, action -> scheduled += delay to action },
        )
        fun retry(): Long = scheduled.removeAt(0).let { (delay, action) -> action(); delay }
    }

    @Test fun retriesThreeTimes() {
        val f = Fixture().apply { failures = 2 }
        f.request.apply(true)
        assertEquals(1500L, f.retry())
        assertEquals(3000L, f.retry())
        assertEquals(listOf<String?>("CalculatorIcon", "CalculatorIcon", "CalculatorIcon"), f.calls)
        assertEquals(0, f.scheduled.size)
    }

    @Test fun noCallWhenAlreadySet() {
        val f = Fixture().apply { current = "CalculatorIcon" }
        f.request.apply(true)
        assertEquals(emptyList(), f.calls)
        f.current = null
        f.request.apply(false)
        assertEquals(emptyList(), f.calls)
    }

    @Test fun givesUpAfterThreeRetries() {
        val f = Fixture().apply { failures = 10 }
        f.request.apply(true)
        assertEquals(listOf(1500L, 3000L, 4500L), List(3) { f.retry() })
        assertEquals(4, f.calls.size)
        assertEquals(0, f.scheduled.size)
    }

    @Test fun unsupportedDoesNothing() {
        val f = Fixture().apply { supported = false }
        f.request.apply(true)
        assertEquals(emptyList(), f.calls)
    }

    @Test fun disablingRestoresPrimaryAndCancelsStaleRetry() {
        val f = Fixture().apply { failures = 1 }
        f.request.apply(true)
        f.request.apply(false)
        f.retry()
        assertEquals(listOf<String?>("CalculatorIcon"), f.calls)
        f.current = "CalculatorIcon"
        f.request.apply(false)
        assertEquals(listOf("CalculatorIcon", null), f.calls)
    }
}
