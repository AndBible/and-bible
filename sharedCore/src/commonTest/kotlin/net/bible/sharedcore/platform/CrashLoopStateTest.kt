package net.bible.sharedcore.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashLoopStateTest {
    private class Store : CrashPreferencesStore {
        override var running = false
        override var count = 0
        override var time = 0L
    }
    private val store = Store()
    private var now = 10_000L
    private val tracker = CrashLoopState(store) { now }

    @Test fun dirtyStartCountsACrash() {
        store.running = true
        tracker.onLaunch()
        assertEquals(1, store.count)
        assertEquals(now, store.time)
        now += 100
        tracker.onLaunch()
        assertEquals(2, store.count)
        assertEquals(10_000L, store.time)
        assertTrue(store.running)
    }

    @Test fun cleanExitDoesNotCount() {
        tracker.onLaunch()
        assertTrue(store.running)
        tracker.onCleanExit()
        assertFalse(store.running)
        tracker.onLaunch()
        assertEquals(0, store.count)
    }

    @Test fun twoCrashesWithinAnHourOfferRecovery() {
        repeat(3) { tracker.onLaunch(); now += 1_000 }
        assertTrue(tracker.shouldOfferRecovery())
        assertEquals(0, store.count)
        assertEquals(0L, store.time)
        assertFalse(tracker.shouldOfferRecovery())
    }

    @Test fun oldCrashResets() {
        store.count = 2
        store.time = now
        now += 3_600_001
        assertFalse(tracker.shouldOfferRecovery())
        assertEquals(0, store.count)
        assertEquals(0L, store.time)
    }

    @Test fun exactHourAndMissingTimestampDoNotOfferRecovery() {
        store.count = 2
        store.time = now
        now += 3_600_000
        assertFalse(tracker.shouldOfferRecovery())
        assertEquals(2, store.count)
        store.time = 0
        assertFalse(tracker.shouldOfferRecovery())
    }

    @Test fun markerClearedBeforeRecoveryScreen() {
        repeat(3) { tracker.onLaunch() }
        assertTrue(tracker.shouldOfferRecovery())
        assertFalse(store.running, "a kill on the recovery screen must not count as another crash")
    }
}
