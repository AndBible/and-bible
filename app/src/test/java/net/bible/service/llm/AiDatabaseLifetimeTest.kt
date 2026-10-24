package net.bible.service.llm

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiDatabaseLifetimeTest {
    @Test fun replacementJoinsObserverAndQueuesWritesUntilItsWholeBlockFinishes() = runBlocking {
        val lifetime = AiDatabaseLifetime()
        val entered = CompletableDeferred<Unit>()
        val finishing = CompletableDeferred<Unit>()
        val releaseObserver = CompletableDeferred<Unit>()
        val replaced = CompletableDeferred<Unit>()
        val releaseReplace = CompletableDeferred<Unit>()
        val observer = launch {
            lifetime.observe {
                entered.complete(Unit)
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { finishing.complete(Unit); releaseObserver.await() } }
            }
        }
        entered.await()
        val replacement = launch { lifetime.replace { replaced.complete(Unit); releaseReplace.await() } }
        finishing.await()
        var wrote = false
        val writer = launch { lifetime.use { wrote = true } }
        yield()
        assertFalse(replaced.isCompleted)
        assertFalse(wrote)
        releaseObserver.complete(Unit)
        replaced.await()
        assertFalse(wrote)
        releaseReplace.complete(Unit)
        replacement.join()
        writer.join()
        assertTrue(wrote)
        observer.cancelAndJoin()
    }

    @Test fun failureCancellationAndExplicitNestingReleaseAdmissionAndRejectEscapedTokens() = runBlocking {
        val lifetime = AiDatabaseLifetime()
        var escaped: AiDatabaseLifetime.Replacement? = null
        try {
            lifetime.replace { token ->
                escaped = token
                lifetime.replace(token) { assertSame(token, it) }
                throw IllegalArgumentException("failed replacement")
            }
            fail("expected failure")
        } catch (_: IllegalArgumentException) { }
        lifetime.use { }
        assertThrows(IllegalStateException::class.java) { lifetime.requireOwner(escaped!!) }
        val entered = CompletableDeferred<Unit>()
        val replacement = launch { lifetime.replace { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        replacement.cancelAndJoin()
        lifetime.use { }
        val wrong = AiDatabaseLifetime()
        lifetime.replace { token ->
            assertThrows(IllegalStateException::class.java) { wrong.requireOwner(token) }
        }
        Unit
    }

    @Test fun overlappingReplacementsNeverShareOwnership() = runBlocking {
        val lifetime = AiDatabaseLifetime()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var secondEntered = false
        val first = launch { lifetime.replace { entered.complete(Unit); release.await() } }
        entered.await()
        val second = launch { lifetime.replace { secondEntered = true } }
        yield()
        assertFalse(secondEntered)
        release.complete(Unit)
        first.join()
        second.join()
        assertTrue(secondEntered)
    }

    /** An owned read cannot be interrupted mid-step (native): replace waits for it before its block closes anything. */
    @Test fun replacementJoinsAnOwnedJobBeforeItsBlockRuns() = runBlocking {
        val lifetime = AiDatabaseLifetime()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stepEnded = false
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await(); stepEnded = true }
        }
        lifetime.ownUntilReplace(job)
        job.start()
        entered.await()
        var stepEndedWhenBlockRan: Boolean? = null
        val replacement = launch(Dispatchers.Default) { lifetime.replace { stepEndedWhenBlockRan = stepEnded } }
        delay(200)
        assertNull("replace ran its block under the in-flight step", stepEndedWhenBlockRan)
        release.complete(Unit)
        replacement.join()
        assertEquals(true, stepEndedWhenBlockRan)
    }

    /** An owned job queued for admission behind the replace would deadlock a plain join; it is cancelled instead. */
    @Test fun anOwnedJobWaitingForAdmissionIsCancelledNotDeadlocked() = runBlocking {
        val lifetime = AiDatabaseLifetime()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch { lifetime.use { entered.complete(Unit); release.await() } }
        entered.await()
        val replacement = launch { lifetime.replace { } }
        yield()
        // Queued for admission behind the replacement: once the replace holds admission, a bare join would wait forever.
        var used = false
        val job = launch(start = CoroutineStart.LAZY) { lifetime.use { used = true } }
        lifetime.ownUntilReplace(job)
        job.start()
        yield()
        release.complete(Unit)
        withTimeout(10_000) { replacement.join() }
        holder.join()
        assertTrue(job.isCancelled)
        assertFalse(used)
    }
}
