package net.bible.sharedui.ios.startup

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import net.bible.sharedui.host.startup.IosStartupOwner

@OptIn(ExperimentalCoroutinesApi::class)
class IosStartupOwnerTest {
    @Test fun orphanedStartupFailureRetriesOnFirstForegroundJoin() = orphanedFailureRetries(false, true)
    @Test fun orphanedDatabaseFailureRetriesWithoutRepeatingStartup() = orphanedFailureRetries(true, true)
    @Test fun startupRetryCannotSelectFailureBeforeQueuedCompletionCleanup() = orphanedFailureRetries(false, false)
    @Test fun databaseRetryCannotSelectFailureBeforeQueuedCompletionCleanup() = orphanedFailureRetries(true, false)

    private fun orphanedFailureRetries(database: Boolean, drainCompletion: Boolean) = runTest {
        val dispatcher = OwnedQueueDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val gate = CompletableDeferred<Unit>()
        var starts = 0
        var opens = 0
        val owner = IosStartupOwner(scope, {
            starts++
            if (!database && starts == 1) { withTimeout(5_000) { gate.await() }; error("orphan startup failed") }
            "ready"
        }, {
            opens++
            if (database && opens == 1) { withTimeout(5_000) { gate.await() }; error("orphan database failed") }
        })
        suspend fun joinOwner() { if (database) owner.openDatabases() else assertEquals("ready", owner.ensureStarted()) }
        val soleWaiter = backgroundScope.async { joinOwner() }
        var foreground: Deferred<Unit>? = null
        try {
            runCurrent()
            dispatcher.drain()
            runCurrent()
            dispatcher.drain()
            assertEquals(1, starts)
            assertEquals(if (database) 1 else 0, opens)
            soleWaiter.cancelAndJoin()
            gate.complete(Unit)
            // Run only the failed operation, leaving any dispatched completion cleanup queued.
            dispatcher.runNext()
            if (drainCompletion) dispatcher.drain()
            foreground = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { joinOwner() }
            assertFalse(foreground.isCompleted, "First foreground join must start retry, not consume cached failure")
            dispatcher.drain()
            runCurrent()
            dispatcher.drain()
            foreground.await()
            assertEquals(if (database) 1 else 2, starts)
            assertEquals(if (database) 2 else 0, opens)
        } finally {
            gate.complete(Unit)
            soleWaiter.cancel()
            foreground?.cancel()
            scope.cancel()
            dispatcher.drain()
        }
    }

    private class OwnedQueueDispatcher : CoroutineDispatcher() {
        private val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queued.addLast(block) }
        fun runNext() { check(queued.isNotEmpty()) { "Expected owned continuation" }; queued.removeFirst().run() }
        fun drain() {
            var steps = 0
            while (queued.isNotEmpty()) { check(++steps < 100) { "Unbounded owned cleanup" }; runNext() }
        }
    }

    @Test fun concurrentWaitersOwnOneStartupAndOneDatabaseOpen() = runTest {
        val gate = CompletableDeferred<Unit>()
        var starts = 0
        var opens = 0
        val owner = IosStartupOwner(CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])), { starts++; gate.await(); "parts" }, { opens++ })
        val foreground = async { owner.openDatabases() }
        val background = async { owner.openDatabases() }
        try {
            runCurrent()
            assertEquals(1, starts)
            gate.complete(Unit)
            foreground.await(); background.await()
            assertEquals(1, opens)
        } finally { gate.complete(Unit); foreground.cancel(); background.cancel() }
    }

    @Test fun cancellingBackgroundStartupWaiterDoesNotCancelForegroundJoin() = runTest {
        val gate = CompletableDeferred<Unit>()
        var starts = 0
        var finishes = 0
        val owner = IosStartupOwner(CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])), { starts++; gate.await(); finishes++; "ready" }, {})
        val background = async { owner.ensureStarted() }
        val foreground = async { owner.ensureStarted() }
        try {
            runCurrent()
            background.cancelAndJoin()
            gate.complete(Unit)
            assertEquals("ready", foreground.await())
            assertEquals(1, starts)
            assertEquals(1, finishes)
        } finally { gate.complete(Unit); background.cancel(); foreground.cancel() }
    }

    @Test fun cancellingForegroundDatabaseWaiterDoesNotCancelBackgroundOpen() = runTest {
        val gate = CompletableDeferred<Unit>()
        var opens = 0
        var finished = 0
        val owner = IosStartupOwner(CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])), { "ready" }, { opens++; gate.await(); finished++ })
        val foreground = async { owner.openDatabases() }
        val background = async { owner.openDatabases() }
        try {
            runCurrent()
            foreground.cancelAndJoin()
            gate.complete(Unit)
            background.await()
            assertEquals(1, opens)
            assertEquals(1, finished)
        } finally { gate.complete(Unit); foreground.cancel(); background.cancel() }
    }

    @Test fun failedAttemptCanRetryAndSuccessfulAttemptIsRetained() = runTest {
        var starts = 0
        var opens = 0
        val owner = IosStartupOwner(CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])), { starts++; if (starts == 1) error("partial startup"); "ready" }, { opens++ })
        assertFailsWith<IllegalStateException> { owner.ensureStarted() }
        assertEquals("ready", owner.ensureStarted())
        assertEquals("ready", owner.ensureStarted())
        owner.openDatabases(); owner.openDatabases()
        assertEquals(2, starts)
        assertEquals(1, opens)
    }

    @Test fun databaseOpenFailureRetriesWithoutRepeatingStartup() = runTest {
        var starts = 0
        var opens = 0
        val owner = IosStartupOwner(CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])), { starts++; "ready" }, { opens++; if (opens == 1) error("database locked") })
        assertFailsWith<IllegalStateException> { owner.openDatabases() }
        owner.openDatabases()
        assertEquals(1, starts)
        assertEquals(2, opens)
    }
}
