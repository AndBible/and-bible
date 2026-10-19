package net.bible.sharedcore.platform

import kotlin.test.*

class CrashRecoveryAdmissionTest {
    private class Store(override var running: Boolean, override var count: Int, override var time: Long) : CrashPreferencesStore

    @Test fun backgroundFirstPredictsDirtyLaunchWithoutMutatingCounters() {
        val store = Store(true, 1, 1_000)
        val state = CrashLoopState(store) { 2_000 }
        assertTrue(state.wouldOfferRecoveryAfterLaunch(false))
        assertEquals(1, store.count)
        assertEquals(1_000, store.time)
        assertTrue(store.running)
        state.onLaunch()
        assertTrue(state.shouldOfferRecovery())
    }

    @Test fun uiFirstDoesNotCountSameLaunchTwice() {
        val store = Store(true, 0, 0)
        val state = CrashLoopState(store) { 2_000 }
        state.onLaunch()
        assertFalse(state.wouldOfferRecoveryAfterLaunch(true))
        assertEquals(1, store.count)
        assertEquals(2_000, store.time)
    }

    @Test fun expiredWindowDoesNotBlockBackgroundOrResetPreferences() {
        val store = Store(true, 3, 1_000)
        assertFalse(CrashLoopState(store) { 3_602_000 }.wouldOfferRecoveryAfterLaunch(false))
        assertEquals(3, store.count)
        assertEquals(1_000, store.time)
    }

    @Test fun cleanLaunchWithTwoRecentCrashesStillNeedsRecovery() {
        assertTrue(CrashLoopState(Store(false, 2, 1_000)) { 2_000 }.wouldOfferRecoveryAfterLaunch(false))
    }
}
