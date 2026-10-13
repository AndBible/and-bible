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
}
