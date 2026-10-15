package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosSyncSchedulerTest {
    @Test fun cancellingReturnedHandleOwnsWorkerAndBlocksReplacementUntilStopped() = runTest {
        var calls = 0
        var stopped = 0
        var ends = 0
        val scheduler = IosSyncScheduler(this, {
            calls++
            try { CompletableDeferred<Boolean>().await() } finally { stopped++ }
        }, { 1uL }, { ends++ })
        val first = scheduler.startDatabaseSync()
        runCurrent()
        first.cancel()
        assertSame(first, scheduler.startDatabaseSync(), "Cancellation is not worker completion")
        assertEquals(1, calls)
        runCurrent()
        assertEquals(1, stopped)
        assertEquals(1, ends)
        val second = scheduler.startDatabaseSync()
        assertNotSame(first, second)
        runCurrent()
        assertEquals(2, calls)
        second.cancel()
        runCurrent()
        assertEquals(2, stopped)
        assertEquals(2, ends)
    }

    @Test fun coalescesAndStartsANewRunAfterCompletion() = runTest {
        val gate = CompletableDeferred<Boolean>()
        var calls = 0
        val ended = mutableListOf<ULong>()
        val scheduler = IosSyncScheduler(this, { calls++; gate.await() }, { 42uL }, { ended += it })
        val first = scheduler.startDatabaseSync()
        assertSame(first, scheduler.startDatabaseSync())
        runCurrent()
        assertEquals(1, calls)
        gate.complete(true)
        runCurrent()
        assertTrue(first.await())
        val next = scheduler.startDatabaseSync()
        assertNotSame(first, next)
        runCurrent()
        assertTrue(next.await())
        assertEquals(2, calls)
        assertEquals(listOf(42uL, 42uL), ended)
    }

    @Test fun falseAndThrowAreFalseResults() = runTest {
        var calls = 0
        var ends = 0
        val scheduler = IosSyncScheduler(this, {
            if (calls++ == 0) false else error("sync failed")
        }, { 1uL }, { ends++ })
        val first = scheduler.startDatabaseSync()
        runCurrent()
        assertFalse(first.await())
        val second = scheduler.startDatabaseSync()
        runCurrent()
        assertFalse(second.await())
        assertEquals(2, ends)
    }

    @Test fun repeatedExpirationResolvesFalseAndEndsOnce() = runTest {
        var expire: () -> Unit = {}
        var ends = 0
        var stopped = false
        val scheduler = IosSyncScheduler(this, {
            try { CompletableDeferred<Boolean>().await() } finally { stopped = true }
        }, { expire = it; 7uL }, { ends++ })
        val result = scheduler.startDatabaseSync()
        runCurrent()
        expire()
        expire()
        assertFalse(result.await())
        runCurrent()
        assertTrue(stopped)
        assertEquals(1, ends)
    }

    @Test fun expirationDuringBeginEndsTheReturnedIdentifierWithoutStartingSync() = runTest {
        var calls = 0
        val ended = mutableListOf<ULong>()
        val scheduler = IosSyncScheduler(this, { calls++; true }, { expire -> expire(); expire(); 9uL }, { ended += it })
        val result = scheduler.startDatabaseSync()
        runCurrent()
        assertFalse(result.await())
        assertEquals(0, calls)
        assertEquals(listOf(9uL), ended)
    }

    @Test fun expiredRunDoesNotCompleteOrEndItsReplacement() = runTest {
        val expirations = mutableListOf<() -> Unit>()
        val ended = mutableListOf<ULong>()
        val gate = CompletableDeferred<Boolean>()
        var begins = 0uL
        val scheduler = IosSyncScheduler(this, { gate.await() }, {
            expirations += it
            ++begins
        }, { ended += it })
        val first = scheduler.startDatabaseSync()
        runCurrent()
        expirations[0]()
        assertSame(first, scheduler.startDatabaseSync())
        runCurrent()
        val second = scheduler.startDatabaseSync()
        assertNotSame(first, second)
        runCurrent()
        expirations[0]()
        assertFalse(second.isCompleted)
        gate.complete(true)
        runCurrent()
        assertFalse(first.await())
        assertTrue(second.await())
        assertEquals(listOf(1uL, 2uL), ended)
    }

    @Test fun immediateCompletionAndLateExpirationEndOnce() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var expire: () -> Unit = {}
            var ends = 0
            val scheduler = IosSyncScheduler(scope, { true }, { expire = it; 2uL }, { ends++ })
            val result = scheduler.startDatabaseSync()
            assertTrue(result.isCompleted)
            expire()
            expire()
            assertEquals(1, ends)
        } finally { scope.cancel() }
    }

    @Test fun scopeCancellationResolvesFalseEvenBeforeWorkStarts() = runTest {
        val owner = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + owner)
        var ends = 0
        val scheduler = IosSyncScheduler(scope, { true }, { 3uL }, { ends++ })
        val result = scheduler.startDatabaseSync()
        owner.cancel()
        runCurrent()
        assertFalse(result.await())
        assertEquals(1, ends)
    }
}
