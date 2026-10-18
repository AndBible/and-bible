package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.documents.DocumentSyncActions
import net.bible.sharedcore.platform.UserNotifier
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosDocumentSyncStarterTest {
    private val events = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private var drops = 0
    private var begins = 0
    private var ends = mutableListOf<ULong>()
    private var expire: (() -> Unit)? = null
    private var failure: (String) -> Throwable? = { null }
    private var gate: CompletableDeferred<Unit>? = null
    private var gate2: CompletableDeferred<Unit>? = null
    private var stubborn = false
    private var onRefresh: suspend () -> Unit = {}

    private val actions = object : DocumentSyncActions {
        private suspend fun run(name: String, i: String) {
            events += "$name $i"
            if (i == "A") {
                if (stubborn) withContext(NonCancellable) { gate?.await() } else gate?.await()
            }
            if (i == "C") gate2?.await()
            failure(i)?.let { throw it }
        }
        override suspend fun push(initials: String) = run("push", initials)
        override suspend fun download(initials: String, onProgress: (Long, Long) -> Unit) = run("download", initials)
        override suspend fun remove(initials: String) = run("remove", initials)
        override suspend fun purge(initials: String) = run("purge", initials)
        override suspend fun uninstall(initials: String) = run("uninstall", initials)
    }
    private val driver = object : DocumentSyncDriver {
        override val actions = this@IosDocumentSyncStarterTest.actions
        override fun notifyRunning(running: Boolean) { events += "running=$running" }
        override suspend fun refreshCache() { events += "refresh"; onRefresh() }
        override fun dropAdapterForReconnect() { drops++ }
    }
    private val notifier = object : UserNotifier {
        override fun showInfo(message: String, long: Boolean) {}
        override fun showError(message: String, cause: Throwable?) { errors += message }
    }

    private fun kotlinx.coroutines.test.TestScope.starter() = IosDocumentSyncStarter.forDriver(
        backgroundScope, driver, notifier, { "generic" },
        begin = { handler -> begins++; expire = handler; 7uL },
        end = { ends += it },
    )

    @Test fun drainsOpsInOrderBetweenRunningEdges() = runTest {
        val s = starter()
        s.start(push = listOf("A"), download = listOf("B"))
        s.awaitIdle()
        assertEquals(
            listOf("running=true", "running=true", "push A", "running=true", "download B", "refresh", "running=false"),
            events,
        )
        assertEquals(1, begins)
        assertEquals(listOf(7uL), ends)
    }

    @Test fun secondStartDuringDrainJoinsSameSession() = runTest {
        gate = CompletableDeferred()
        val s = starter()
        s.start(push = listOf("A"), download = emptyList())
        runCurrent()
        s.start(push = emptyList(), download = listOf("B"))
        gate!!.complete(Unit)
        s.awaitIdle()
        assertEquals(1, begins)
        assertEquals(1, ends.size)
        assertTrue(events.indexOf("download B") > events.indexOf("push A"))
        assertEquals(1, events.count { it == "running=false" })
    }

    @Test fun emptyStartDoesNothing() = runTest {
        val s = starter()
        s.start(push = emptyList(), download = emptyList())
        s.awaitIdle()
        assertEquals(0, begins)
        assertTrue(events.isEmpty())
    }

    @Test fun userFacingReconnectNotifiesDropsAdapterAndAbandonsQueue() = runTest {
        failure = { if (it == "A") CloudSyncUserFacingException("bad password", true) else null }
        val s = starter()
        s.start(push = listOf("A", "C"), download = listOf("B"))
        s.awaitIdle()
        assertEquals(listOf("bad password"), errors)
        assertEquals(1, drops)
        assertTrue(events.none { it.endsWith(" B") || it.endsWith(" C") })
        assertEquals("running=false", events.last())
    }

    @Test fun userFacingWithoutReconnectKeepsAdapter() = runTest {
        failure = { CloudSyncUserFacingException("full", false) }
        val s = starter()
        s.start(push = listOf("A"), download = emptyList())
        s.awaitIdle()
        assertEquals(listOf("full"), errors)
        assertEquals(0, drops)
    }

    @Test fun failedShowsGenericErrorAndContinues() = runTest {
        failure = { if (it == "A") IllegalStateException("boom") else null }
        val s = starter()
        s.start(push = listOf("A"), download = listOf("B"))
        s.awaitIdle()
        assertEquals(listOf("generic"), errors)
        assertTrue("download B" in events)
    }

    @Test fun retryLaterIsSilentAndContinues() = runTest {
        failure = { if (it == "A") IOException("offline") else null }
        val s = starter()
        s.start(push = listOf("A"), download = listOf("B"))
        s.awaitIdle()
        assertTrue(errors.isEmpty())
        assertTrue("download B" in events)
    }

    @Test fun stopCancelsDrainEndsTaskAndClearsRunning() = runTest {
        gate = CompletableDeferred()
        val s = starter()
        s.start(push = listOf("A"), download = listOf("B"))
        runCurrent()
        s.stop()
        s.awaitIdle()
        assertEquals(listOf(7uL), ends)
        assertEquals("running=false", events.last())
        assertTrue("download B" !in events)
        // A new start after stop begins a fresh session.
        gate = null
        s.start(push = listOf("C"), download = emptyList())
        s.awaitIdle()
        assertEquals(2, begins)
        assertTrue("push C" in events)
    }

    @Test fun expirationCancelsDrain() = runTest {
        gate = CompletableDeferred()
        val s = starter()
        s.start(push = listOf("A"), download = listOf("B"))
        runCurrent()
        expire!!.invoke()
        s.awaitIdle()
        assertEquals(listOf(7uL), ends)
        assertEquals("running=false", events.last())
        assertTrue("download B" !in events)
    }

    @Test fun expiryEndsBackgroundTaskSynchronouslyAndExactlyOnce() = runTest {
        gate = CompletableDeferred()
        stubborn = true   // the op ignores cancellation, like a blocking coordinator/WebDAV call
        val s = starter()
        s.start(push = listOf("A"), download = emptyList())
        runCurrent()
        expire!!.invoke()
        // Ended inside the handler, while the drain is still stuck in the op.
        assertEquals(listOf(7uL), ends)
        assertTrue("running=false" !in events)
        gate!!.complete(Unit)
        s.awaitIdle()
        assertEquals(listOf(7uL), ends)   // finally did not end it a second time
    }

    @Test fun expiryAfterNormalEndDoesNotEndTwice() = runTest {
        val s = starter()
        s.start(push = listOf("A"), download = emptyList())
        s.awaitIdle()
        expire!!.invoke()
        assertEquals(listOf(7uL), ends)
    }

    @Test fun staleSessionDoesNotTouchNewSession() = runTest {
        gate = CompletableDeferred(); gate2 = CompletableDeferred(); stubborn = true
        failure = { if (it == "A") CloudSyncUserFacingException("stale", true) else null }
        val s = starter()
        s.start(push = listOf("A"), download = emptyList())
        runCurrent()
        s.stop()
        s.start(push = listOf("C"), download = listOf("D"))
        runCurrent()
        gate!!.complete(Unit)   // the old op now unwinds with a user-facing failure
        runCurrent()
        assertEquals(0, drops)
        gate2!!.complete(Unit)
        s.awaitIdle()
        assertTrue("download D" in events)   // the new session's queue was not cleared
        assertEquals("running=false", events.last())
    }

    @Test fun lateEnqueueDuringRefreshIsNotLostAndNoSecondConsumer() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        var first = true
        onRefresh = { if (first) { first = false; refreshGate.await() } }
        val s = starter()
        s.start(push = listOf("A"), download = emptyList())
        runCurrent()
        s.start(push = emptyList(), download = listOf("B"))
        refreshGate.complete(Unit)
        s.awaitIdle()
        assertEquals(1, begins)
        assertEquals(listOf(7uL), ends)
        assertEquals(1, events.count { it == "download B" })
        assertEquals(1, events.count { it == "running=false" })
        assertEquals("running=false", events.last())
    }
}
