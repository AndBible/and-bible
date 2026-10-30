package net.bible.sharedui.assembly

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import net.bible.sharedui.host.startup.HostStartupOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosUiStartupTest {
    @Test fun cancelledUiWaiterDoesNotPoisonReplacementHostStartup() = awaitStartupOnMain {
        val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var starts = 0
        var opens = 0
        val owner = HostStartupOwner(processScope, {
            starts++
            entered.complete(Unit)
            withTimeout(5_000) { gate.await() }
            Unit
        }, { opens++ })
        val state = HostAppStartupState(open = { owner.openDatabases() })
        suspend fun attach() = awaitIosUiStartup(
            ensureStarted = { owner.ensureStarted() }, state = state, processScope = processScope,
            recoveryBackup = false, recoveryReady = { error("not a recovery backup") },
        )
        val first = CoroutineScope(kotlin.coroutines.coroutineContext).async { attach() }
        var replacement: kotlinx.coroutines.Deferred<Unit>? = null
        try {
            withTimeout(5_000) { entered.await() }
            first.cancelAndJoin()
            assertFalse(state.phase.value is StartupPhase.Failed, "Disposing UI waiter must not publish permanent startup failure")
            val second = CoroutineScope(kotlin.coroutines.coroutineContext).async { attach() }
            replacement = second
            gate.complete(Unit)
            withTimeout(5_000) { second.await() }
            val phase = withTimeout(5_000) { state.phase.first { it != StartupPhase.Starting } }
            assertEquals(StartupPhase.Ready, phase)
            assertTrue(first.isCancelled)
            assertEquals(1, starts)
            assertEquals(1, opens)
        } finally {
            gate.complete(Unit)
            first.cancel()
            replacement?.cancel()
            processScope.cancel()
            withTimeout(5_000) { owner.joinInFlight() }
        }
    }
}
