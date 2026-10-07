package net.bible.android.view.compose

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.view.activity.nav.subscribeToDocumentSyncRunning
import net.bible.sharedcore.event.EventSource
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudDocumentsProgressSubscriptionTest {

    @Test
    fun aFastDrainStillDeliversBothEdgesInOrder() = runTest {
        val source = EventSource<Boolean>()
        val seen = mutableListOf<Boolean>()
        val stop = subscribeToDocumentSyncRunning(backgroundScope, source) { seen += it }
        runCurrent()
        source.emit(true)
        source.emit(false) // both before the collector runs
        assertEquals("collector has not run yet", emptyList<Boolean>(), seen)
        runCurrent()
        assertEquals(listOf(true, false), seen)
        stop()
        runCurrent()
        source.emit(true)
        runCurrent()
        assertEquals("nothing after stop", listOf(true, false), seen)
    }
}
