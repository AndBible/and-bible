package net.bible.android.control.activity

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.event.EventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackgroundErrorRouterTest {
    /** #4031: a sync error that repeats the previous run's failure never pops a dialog again, whatever the preference. */
    @Test fun repeatedErrorGoesToTheListWithAToastEvenWhenThePreferenceIsOff() = runTest {
        val center = ActivityCenter(backgroundScope) { currentTime }
        val source = SyncActivitySource(center, EventSource<Boolean>()) { "Sync" }
        val router = BackgroundErrorRouter({ false }, source) { "$it (see activities)" }
        assertNull(router.route("sync error", background = true, kind = ActivityKind.SYNC))
        assertEquals("sync error (see activities)", router.route("sync error", background = true, kind = ActivityKind.SYNC, repeated = true))
        runCurrent()
        assertEquals(1, center.items.value.size, "Both failures share one row")
        source.close()
    }
}
