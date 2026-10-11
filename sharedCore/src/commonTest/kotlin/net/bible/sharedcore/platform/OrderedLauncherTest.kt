package net.bible.sharedcore.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OrderedLauncherTest {
    @Test fun joinWaitsForTheTail() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val launcher = OrderedLauncher(scope)
        val list = mutableListOf<String>()
        var seen = emptyList<String>()
        launcher.launch("k") { delay(10); list += "first" }
        launcher.launch("k") { list += "second" }
        launch { launcher.join("k"); seen = list.toList() }
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), seen)
        scope.cancel()
    }

    @Test fun joinOnUnknownKeyReturns() = runTest {
        OrderedLauncher(this).join("unknown")
    }

    @Test fun sameKeyRunsInSubmissionOrderEvenWhenTheFirstSuspendsLonger() = runTest {
        val l = OrderedLauncher(this)
        val log = mutableListOf<String>()
        l.launch("w1") { delay(100); log += "save" }
        l.launch("w1") { log += "reload" }
        advanceUntilIdle()
        assertEquals(listOf("save", "reload"), log)
    }

    @Test fun differentKeysDoNotWaitForEachOther() = runTest {
        val l = OrderedLauncher(this)
        val gate = CompletableDeferred<Unit>()
        val log = mutableListOf<String>()
        l.launch("w1") { gate.await(); log += "w1" }
        l.launch("w2") { log += "w2" }
        advanceUntilIdle()
        assertEquals(listOf("w2"), log)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("w2", "w1"), log)
    }

    @Test fun aFailureDoesNotStopLaterBlocksOfTheSameKey() = runTest {
        val l = OrderedLauncher(this)
        val log = mutableListOf<String>()
        l.launch("w1") { error("boom") }
        l.launch("w1") { log += "after" }
        advanceUntilIdle()
        assertEquals(listOf("after"), log)
    }

    @Test fun cancellingTheCallerDoesNotCancelAQueuedWrite() = runTest {
        // Review Focus #4: the launcher's own scope owns the work, not the submitter.
        val l = OrderedLauncher(this)
        val log = mutableListOf<String>()
        val submitter = TestScope(StandardTestDispatcher(testScheduler))
        submitter.launch { l.launch("w1") { delay(10); log += "written" } }
        submitter.testScheduler.runCurrent(); submitter.cancel()
        advanceUntilIdle()
        assertEquals(listOf("written"), log)
    }
}
