package net.bible.sharedcore.cloud

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CloudDocumentsLiveRefreshTest {
    @Test fun reloads_periodically_while_running_and_stops_when_it_ends() = runTest {
        val running = MutableStateFlow(false)
        var reloads = 0
        val job = launch { runCloudDocumentsLiveRefresh(running, intervalMs = 100) { reloads++ } }
        runCurrent()
        advanceTimeBy(1000)
        assertEquals(0, reloads, "idle: no reload")

        running.value = true
        runCurrent()
        advanceTimeBy(350)
        assertEquals(3, reloads, "reloads during the run, without waiting for it to end")

        running.value = false
        runCurrent()
        val atStop = reloads
        advanceTimeBy(1000)
        assertEquals(atStop, reloads, "no reload after the sync ends")
        job.cancel()
    }

    @Test fun already_running_at_start_reloads() = runTest {
        var reloads = 0
        val job = launch { runCloudDocumentsLiveRefresh(MutableStateFlow(true), intervalMs = 100) { reloads++ } }
        runCurrent()
        advanceTimeBy(250)
        assertEquals(2, reloads)
        job.cancel()
    }
}
