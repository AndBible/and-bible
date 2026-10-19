package net.bible.sharedui.ios.startup

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosStartupOwnerTest {
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
