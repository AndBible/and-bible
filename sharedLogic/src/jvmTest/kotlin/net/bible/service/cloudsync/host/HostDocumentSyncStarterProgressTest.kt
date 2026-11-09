package net.bible.service.cloudsync.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.documents.DocumentSyncActions
import net.bible.service.cloudsync.documents.DocumentSyncProgressTracker
import net.bible.sharedcore.cloud.DocumentSyncInterruptionReason
import net.bible.sharedcore.cloud.DocumentSyncOperation
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HostDocumentSyncStarterProgressTest {
    private class Driver : DocumentSyncDriver {
        var action: suspend (String, (Long, Long) -> Unit) -> Unit = { _, _ -> }
        var refresh: suspend () -> Unit = {}
        val calls = mutableListOf<String>()
        val running = mutableListOf<Boolean>()
        var drops = 0
        override val actions = object : DocumentSyncActions {
            private suspend fun run(kind: String, initials: String, progress: (Long, Long) -> Unit = { _, _ -> }) {
                calls += "$kind:$initials"
                action(initials, progress)
            }
            override suspend fun push(initials: String) = run("push", initials)
            override suspend fun download(initials: String, onProgress: (Long, Long) -> Unit) = run("download", initials, onProgress)
            override suspend fun remove(initials: String) = run("remove", initials)
            override suspend fun purge(initials: String) = run("purge", initials)
            override suspend fun uninstall(initials: String) = run("uninstall", initials)
        }
        override fun notifyRunning(running: Boolean) { this.running += running }
        override suspend fun refreshCache() = refresh()
        override fun dropAdapterForReconnect() { drops++ }
    }

    private class Fixture(scope: CoroutineScope) {
        val tracker = DocumentSyncProgressTracker()
        val driver = Driver()
        val states = mutableListOf<DocumentSyncProgress>()
        val expiration = mutableListOf<() -> Unit>()
        val ends = mutableListOf<ULong>()
        val subscription = tracker.observe { states += it }
        val starter = HostDocumentSyncStarter.forDriver(scope, driver, { "generic" },
            begin = { expiration += it; expiration.size.toULong() }, end = { ends += it }, tracker = tracker)
        val current get() = tracker.state.value
        fun assertTerminal(status: DocumentSyncProgressStatus, processed: Int, total: Int, failed: Int = 0, deferred: Int = 0) {
            assertEquals(status, current.status)
            assertEquals(processed, current.processed)
            assertEquals(total, current.total)
            assertEquals(failed, current.failed)
            assertEquals(deferred, current.deferred)
            assertEquals(1, states.count { it.sessionId == current.sessionId && it.status != DocumentSyncProgressStatus.RUNNING })
            subscription.cancel()
        }
    }

    @Test fun downloadProgressIsVisibleBeforeConsumerCompletes() = runTest {
        val f = Fixture(backgroundScope)
        val release = CompletableDeferred<Unit>()
        var progress: ((Long, Long) -> Unit)? = null
        f.driver.action = { _, callback -> progress = callback; release.await() }
        f.starter.start(push = emptyList(), download = listOf("A"))
        assertEquals(DocumentSyncProgressStatus.RUNNING, f.current.status)
        assertEquals(1, f.current.total)
        assertEquals(0, f.current.processed)
        runCurrent()
        progress!!(25, 100)
        assertEquals(25L, f.current.bytesTransferred)
        assertEquals(100L, f.current.totalBytes)
        progress(100, 100)
        assertEquals(DocumentSyncProgressStatus.RUNNING, f.current.status)
        assertEquals(0, f.current.processed)
        release.complete(Unit)
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 1, 1)
    }

    @Test fun lateEnqueueExpandsSameSessionTotal() = runTest {
        val f = Fixture(backgroundScope)
        val refreshGate = CompletableDeferred<Unit>()
        var first = true
        f.driver.refresh = { if (first) { first = false; refreshGate.await() } }
        f.starter.start(push = listOf("A"), download = emptyList())
        runCurrent()
        val session = f.current.sessionId
        assertEquals(DocumentSyncProgressStatus.RUNNING, f.current.status)
        assertEquals(1, f.current.processed)
        f.starter.start(push = emptyList(), download = listOf("B"))
        assertEquals(session, f.current.sessionId)
        assertEquals(2, f.current.total)
        refreshGate.complete(Unit)
        f.starter.awaitIdle()
        assertEquals(listOf("push:A", "download:B"), f.driver.calls)
        assertEquals(1, f.expiration.size)
        assertEquals(listOf(1uL), f.ends)
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 2, 2)
    }

    @Test fun fastQueueFinishesExactlyOnce() = runTest {
        val f = Fixture(backgroundScope)
        f.starter.start(listOf("A"), listOf("B"), listOf("C"), listOf("D"), listOf("E"))
        f.starter.awaitIdle()
        assertEquals(listOf("push:A", "download:B", "remove:C", "purge:D", "uninstall:E"), f.driver.calls)
        assertEquals(DocumentSyncOperation.entries, f.states.mapNotNull { it.currentOperation }.distinct())
        assertEquals(listOf(1uL), f.ends)
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 5, 5)
    }

    @Test fun zeroOpsDoesNotStart() = runTest {
        val f = Fixture(backgroundScope)
        f.starter.start(emptyList(), emptyList())
        f.starter.awaitIdle()
        assertEquals(DocumentSyncProgress(), f.current)
        assertTrue(f.expiration.isEmpty())
        assertTrue(f.driver.running.isEmpty())
        f.subscription.cancel()
    }

    @Test fun unknownTotalHasNoPercent() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.driver.action = { _, progress -> progress(25, -1); gate.await() }
        f.starter.start(emptyList(), listOf("A"))
        runCurrent()
        assertEquals(25L, f.current.bytesTransferred)
        assertNull(f.current.totalBytes)
        gate.complete(Unit)
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 1, 1)
    }

    @Test fun overflowSizedBytesClampSafely() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.driver.action = { _, progress -> progress(Long.MAX_VALUE, Long.MAX_VALUE - 1); gate.await() }
        f.starter.start(emptyList(), listOf("A"))
        runCurrent()
        assertEquals(Long.MAX_VALUE - 1, f.current.bytesTransferred)
        assertEquals(Long.MAX_VALUE - 1, f.current.totalBytes)
        gate.complete(Unit)
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 1, 1)
    }

    @Test fun returnedNoOpMeansQueueFinishedNotInstalled() = runTest {
        val f = Fixture(backgroundScope)
        f.starter.start(listOf("not-installed"), emptyList())
        f.starter.awaitIdle()
        assertTrue(f.states.none { it.bytesTransferred != null })
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 1, 1)
    }

    @Test fun retryLaterIsIncompleteNotSuccess() = runTest {
        val f = Fixture(backgroundScope)
        f.driver.action = { _, _ -> throw IOException("offline") }
        f.starter.start(listOf("A"), emptyList())
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.INCOMPLETE, 1, 1, deferred = 1)
    }

    @Test fun failureContinuesButNeverFinishesClean() = runTest {
        val f = Fixture(backgroundScope)
        f.driver.action = { initials, _ -> if (initials == "A") error("operation failed") }
        f.starter.start(listOf("A", "B"), emptyList())
        f.starter.awaitIdle()
        assertEquals(listOf("push:A", "push:B"), f.driver.calls)
        f.assertTerminal(DocumentSyncProgressStatus.FAILED, 2, 2, failed = 1)
    }

    @Test fun refreshFailureIsIncomplete() = runTest {
        val f = Fixture(backgroundScope)
        f.driver.refresh = { error("refresh failed") }
        f.starter.start(listOf("A"), emptyList())
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.INCOMPLETE, 1, 1)
    }

    @Test fun operationFailureThenRefreshFailureStaysFailedWithoutDuplicateInterruption() = runTest {
        val f = Fixture(backgroundScope)
        val errors = mutableListOf<UserMessage.ErrorNotification>()
        val subscription = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }
        try {
            f.driver.action = { _, _ -> error("operation failed") }
            f.driver.refresh = { error("refresh failed") }
            f.starter.start(listOf("A"), emptyList())
            f.starter.awaitIdle()
            f.assertTerminal(DocumentSyncProgressStatus.FAILED, 1, 1, failed = 1)
            assertNull(f.current.interruptionReason)
            assertEquals(1, errors.size)
            assertEquals("generic", errors.single().text)
        } finally { subscription.cancel() }
    }

    @Test fun driverOrBeginThrowsCannotStrandRunning() = runTest {
        for (failBegin in listOf(false, true)) {
            val tracker = DocumentSyncProgressTracker()
            val errors = mutableListOf<UserMessage.ErrorNotification>()
            val subscription = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }
            val ends = mutableListOf<ULong>()
            try {
                val s = HostDocumentSyncStarter.forDriver(backgroundScope,
                    driver = { error("driver failed") }, syncErrorMessage = { "generic" },
                    begin = { if (failBegin) error("begin failed") else 1uL }, end = { ends += it }, tracker = tracker)
                s.start(listOf("A"), emptyList())
                s.awaitIdle()
                assertEquals(DocumentSyncProgressStatus.FAILED, tracker.state.value.status)
                assertEquals(1, tracker.state.value.failed)
                assertEquals(0, tracker.state.value.processed)
                assertEquals(1, errors.size)
                assertEquals(if (failBegin) emptyList() else listOf(1uL), ends)
            } finally { subscription.cancel() }
        }
    }

    @Test fun stopRestartRejectsStaleBytesTerminalAndFailure() = runTest {
        val f = Fixture(backgroundScope)
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        var oldProgress: ((Long, Long) -> Unit)? = null
        f.driver.action = { initials, progress ->
            if (initials == "A") withContext(NonCancellable) {
                oldProgress = progress
                oldGate.await()
                progress(100, 100)
                throw CloudSyncUserFacingException("stale", true)
            } else newGate.await()
        }
        val errors = mutableListOf<UserMessage.ErrorNotification>()
        val subscription = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }
        try {
            f.starter.start(emptyList(), listOf("A"))
            runCurrent()
            f.starter.stop()
            assertEquals(DocumentSyncProgressStatus.CANCELLED, f.current.status)
            assertEquals(DocumentSyncInterruptionReason.USER_STOP, f.current.interruptionReason)
            f.starter.start(emptyList(), listOf("B"))
            runCurrent()
            val before = f.current
            oldProgress!!(25, 100)
            oldGate.complete(Unit)
            runCurrent()
            assertEquals(before, f.current)
            assertEquals(0, f.driver.drops)
            assertTrue(errors.isEmpty())
            assertTrue(f.driver.running.none { !it })
            newGate.complete(Unit)
            f.starter.awaitIdle()
            assertEquals(listOf(1uL, 2uL), f.ends)
            f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 1, 1)
        } finally { subscription.cancel(); oldGate.complete(Unit); newGate.complete(Unit) }
    }

    @Test fun expiryOfOldTaskCannotInterruptNewSession() = runTest {
        val f = Fixture(backgroundScope)
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        f.driver.action = { initials, _ -> if (initials == "A") withContext(NonCancellable) { oldGate.await() } else newGate.await() }
        f.starter.start(listOf("A"), emptyList())
        runCurrent()
        f.starter.stop()
        f.starter.start(listOf("B"), emptyList())
        runCurrent()
        val before = f.current
        f.expiration.first()()
        assertEquals(before, f.current)
        assertEquals(listOf(1uL), f.ends)
        oldGate.complete(Unit)
        runCurrent()
        assertEquals(before, f.current)
        newGate.complete(Unit)
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.FINISHED, 1, 1)
    }

    @Test fun backgroundExpirationInterruptsQueue() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.driver.action = { _, _ -> gate.await() }
        f.starter.start(listOf("A"), emptyList())
        runCurrent()
        f.expiration.single()()
        assertEquals(DocumentSyncProgressStatus.INTERRUPTED, f.current.status)
        assertEquals(DocumentSyncInterruptionReason.BACKGROUND_EXPIRED, f.current.interruptionReason)
        assertEquals(listOf(1uL), f.ends)
        f.starter.awaitIdle()
        f.assertTerminal(DocumentSyncProgressStatus.INTERRUPTED, 0, 1)
    }

    @Test fun scopeCancellationInterruptsQueue() = runTest {
        val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
        val owner = CoroutineScope(backgroundScope.coroutineContext + parent)
        val f = Fixture(owner)
        f.driver.action = { _, _ -> CompletableDeferred<Unit>().await() }
        f.starter.start(listOf("A"), emptyList())
        runCurrent()
        owner.cancel()
        f.starter.awaitIdle()
        assertEquals(DocumentSyncInterruptionReason.SCOPE_CANCELLED, f.current.interruptionReason)
        assertEquals(listOf(1uL), f.ends)
        f.assertTerminal(DocumentSyncProgressStatus.INTERRUPTED, 0, 1)
    }

    @Test fun unconfinedStateCollectorCanUseAnotherProducerThreadAndRestart() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.driver.action = { _, _ -> gate.await() }
        var producerWasAvailable = false
        var restarted = false
        var worker: Thread? = null
        val collector = backgroundScope.launch(Dispatchers.Unconfined) {
            f.tracker.state.collect { progress ->
                if (!restarted && progress.status == DocumentSyncProgressStatus.RUNNING && progress.total == 2) {
                    restarted = true
                    val accepted = CountDownLatch(1)
                    worker = thread(name = "state-collector-producer") {
                        f.tracker.nextSessionId()
                        accepted.countDown()
                    }
                    producerWasAvailable = accepted.await(2, TimeUnit.SECONDS)
                    f.starter.stop()
                    f.starter.start(listOf("new"), emptyList())
                }
            }
        }
        try {
            f.starter.start(listOf("A"), emptyList())
            runCurrent()
            f.starter.start(listOf("B"), emptyList())
            worker!!.join(5_000)
            assertTrue(producerWasAvailable, "StateFlow collector resumed with producer lock held")
            assertTrue(restarted)
            assertEquals(1, f.current.total)
        } finally {
            collector.cancel()
            gate.complete(Unit)
            f.starter.stop()
            f.starter.awaitIdle()
            worker?.join(5_000)
            f.subscription.cancel()
        }
    }

    @Test fun blockedObserverCannotDeliverFailedBeforeErrorStreakIsClosed() = runTest {
        val tracker = DocumentSyncProgressTracker()
        val driver = Driver()
        val gate = CompletableDeferred<Unit>()
        driver.action = { initials, _ ->
            if (initials == "A") gate.await()
            error("operation failed")
        }
        val parent = SupervisorJob()
        val owner = CoroutineScope(parent + Dispatchers.Unconfined)
        val s = HostDocumentSyncStarter.forDriver(owner, driver, { "generic" }, { 1uL }, {}, tracker = tracker)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = java.util.concurrent.atomic.AtomicBoolean()
        val startedNext = java.util.concurrent.atomic.AtomicBoolean()
        val errors = java.util.Collections.synchronizedList(mutableListOf<UserMessage.ErrorNotification>())
        val messages = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }
        val observation = tracker.observe {
            if (it.status == DocumentSyncProgressStatus.RUNNING && it.total == 2 && it.processed == 0 && blocked.compareAndSet(false, true)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "observer release timed out" }
            }
            if (it.status == DocumentSyncProgressStatus.FAILED && startedNext.compareAndSet(false, true)) {
                s.start(listOf("C"), emptyList())
            }
        }
        var publisher: Thread? = null
        try {
            s.start(listOf("A"), emptyList())
            publisher = thread(name = "blocked-progress-observer") { s.start(listOf("B"), emptyList()) }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "late-enqueue observer did not enter")
            // This thread finishes both failed operations while the publication thread is blocked.
            gate.complete(Unit)
            release.countDown()
            publisher.join(5_000)
            assertTrue(!publisher.isAlive, "publication did not finish")
            s.awaitIdle()
            assertEquals(2, errors.size)
            assertEquals(listOf(false, true), errors.map { it.repeated }, "second failing drain must be a quiet repeat")
            assertEquals(listOf(false, true), errors.map { it.background })
        } finally {
            release.countDown()
            gate.complete(Unit)
            publisher?.join(5_000)
            observation.cancel()
            messages.cancel()
            s.stop()
            owner.cancel()
        }
    }

    @Test fun recreatedStarterUsesAppScopedSessionIds() = runTest {
        val tracker = DocumentSyncProgressTracker()
        repeat(2) {
            val s = HostDocumentSyncStarter.forDriver(backgroundScope, Driver(), { "generic" }, { 1uL }, {}, tracker = tracker)
            s.start(listOf("A"), emptyList())
            s.awaitIdle()
            assertEquals((it + 1).toLong(), tracker.state.value.sessionId)
            assertEquals(DocumentSyncProgressStatus.FINISHED, tracker.state.value.status)
        }
    }
}
