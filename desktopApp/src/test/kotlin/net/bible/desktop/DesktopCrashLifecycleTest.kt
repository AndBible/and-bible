package net.bible.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import net.bible.sharedui.assembly.StartupPhase
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopCrashLifecycleTest {
    @Test
    fun readyNotifiesOnceOnlyForNormalStartup() = runBlocking {
        val ready = AtomicInteger()
        val state = DesktopStartupState("en", { StartupPhase.Ready }, { error("unused") }, onStartupReady = { ready.incrementAndGet() })
        state.startForRecovery()
        assertEquals(0, ready.get())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            state.launch(scope)
            state.launch(scope)
            withTimeout(10_000) { state.phase.first { it is StartupPhase.Ready } }
            assertEquals(1, ready.get())
        } finally { scope.cancel() }
    }

    @Test
    fun failedStartupDoesNotNotifyReady() = runBlocking {
        val ready = AtomicInteger()
        val state = DesktopStartupState("en", { StartupPhase.Failed(IllegalStateException()) }, { error("unused") }, onStartupReady = { ready.incrementAndGet() })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            state.launch(scope)
            withTimeout(10_000) { state.phase.first { it is StartupPhase.Failed } }
            assertEquals(0, ready.get())
        } finally { scope.cancel() }
    }

    @Test
    fun cleanExitFollowsCompletedPersistenceAndNotThrownException() {
        val events = mutableListOf<String>()
        persistAndMarkClean({ events += "save" }, { events += "clean" })
        assertEquals(listOf("save", "clean"), events)
        assertFailsWith<IllegalStateException> { persistAndMarkClean({ error("save") }, { events += "unexpected" }) }
        assertEquals(listOf("save", "clean"), events)
    }
}
