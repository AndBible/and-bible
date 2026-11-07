package net.bible.android.control.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentSessionChange
import net.bible.service.llm.agent.AgentStopReason
import net.bible.sharedcore.event.EventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentActivitySourceTest {
    @Test
    fun oldTerminalDeliveredAfterReplacementDoesNotStopNewRun() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        val source = AgentActivitySource(center, events, {}, { null }, {
            AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
        })
        val terminalEntered = java.util.concurrent.CountDownLatch(1)
        val releaseTerminal = java.util.concurrent.CountDownLatch(1)
        val session = net.bible.service.llm.agent.AgentSession(ws) { event ->
            if (event is AgentSessionChange.StatusChanged && !event.isRunning) {
                terminalEntered.countDown()
                check(releaseTerminal.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
            events.emit(event)
        }
        val context = net.bible.service.llm.agent.AgentContext(promptId = IdType())
        assertTrue(session.tryStart(context))
        val stopping = kotlin.concurrent.thread { session.stop() }
        assertTrue(terminalEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        try {
            val restarted = session.tryStart(context)
            releaseTerminal.countDown()
            stopping.join(5000)
            if (!restarted) assertTrue(session.tryStart(context))
            assertTrue(center.isRunning(agentActivityId(ws)))
        } finally {
            releaseTerminal.countDown()
            stopping.join(5000)
            source.close()
        }
    }

    @Test
    fun delayedCancellationCleanupDoesNotStopReplacement() = runTest {
        val ws = IdType()
        val session = net.bible.service.llm.agent.AgentSession(ws) {}
        val context = net.bible.service.llm.agent.AgentContext(promptId = IdType())
        val first = kotlinx.coroutines.Job()
        val replacement = kotlinx.coroutines.Job()
        assertTrue(session.tryStart(context, first))
        session.stop()
        assertTrue(session.tryStart(context, replacement))
        session.stop(owner = first)
        assertTrue(session.isRunning)
        assertTrue(replacement.isActive)
        session.stop()
    }

    @Test
    fun reentrantStartCannotOvertakeTerminalSubscribers() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        val context = net.bible.service.llm.agent.AgentContext(promptId = IdType())
        lateinit var session: net.bible.service.llm.agent.AgentSession
        var reentrantStart = true
        var terminalCount = 0
        var runningAtStop = true
        val subscription = events.subscribe { event ->
            if (event is AgentSessionChange.StatusChanged && !event.isRunning) {
                terminalCount++
                runningAtStop = session.isRunning
                if (terminalCount == 1) {
                    session.stop()
                    reentrantStart = session.tryStart(context)
                }
            }
        }
        val source = AgentActivitySource(center, events, {}, { null }, {
            AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
        })
        session = net.bible.service.llm.agent.AgentSession(ws, events::emit)
        assertTrue(session.tryStart(context))
        session.stop()
        assertEquals(false, reentrantStart)
        assertEquals(false, runningAtStop)
        assertEquals(1, terminalCount)
        assertTrue(session.tryStart(context))
        assertTrue(center.isRunning(agentActivityId(ws)))
        subscription.cancel()
        source.close()
    }

    @Test
    fun concurrentStopDuringStartIsDeliveredAfterStart() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val blocker = events.subscribe { event ->
            if (event is AgentSessionChange.StatusChanged && event.isRunning) {
                entered.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val source = AgentActivitySource(center, events, {}, { null }, {
            AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
        })
        val session = net.bible.service.llm.agent.AgentSession(ws, events::emit)
        val job = kotlinx.coroutines.Job()
        val starter = kotlin.concurrent.thread {
            session.tryStart(net.bible.service.llm.agent.AgentContext(promptId = IdType()), job)
        }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            session.stop()
            release.countDown()
            starter.join(5000)
            assertEquals(false, starter.isAlive)
            assertTrue(job.isCancelled)
            assertEquals(false, session.isRunning)
            assertEquals(false, center.isRunning(agentActivityId(ws)))
        } finally {
            release.countDown()
            starter.join(5000)
            blocker.cancel()
            source.close()
        }
    }

    @Test
    fun reentrantStopDuringStartLeavesNoRunningRow() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        lateinit var session: net.bible.service.llm.agent.AgentSession
        val stopper = events.subscribe { event ->
            if (event is AgentSessionChange.StatusChanged && event.isRunning) session.stop()
        }
        val source = AgentActivitySource(center, events, {}, { null }, {
            AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
        })
        session = net.bible.service.llm.agent.AgentSession(ws, events::emit)
        assertTrue(session.tryStart(net.bible.service.llm.agent.AgentContext(promptId = IdType())))
        assertEquals(false, session.isRunning)
        assertEquals(false, center.isRunning(agentActivityId(ws)))
        stopper.cancel()
        source.close()
    }

    @Test
    fun initialSessionLogIsVisible() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        val source = AgentActivitySource(center, events, {}, { null }, {
            AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
        })
        val session = net.bible.service.llm.agent.AgentSession(ws, events::emit)
        assertTrue(session.tryStart(net.bible.service.llm.agent.AgentContext(promptId = IdType())))
        advanceTimeBy(1000); runCurrent()
        assertEquals("Agent started", center.items.value.single().message)
        source.close()
    }

    @Test
    fun runLifecycleAndPermission() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        val stopped = mutableListOf<IdType>()
        val source = AgentActivitySource(center, events, { stopped.add(it) }, { "Study" }, {
            AgentActivityStrings("AI agent", { "AI agent: $it" }, { "Waiting: $it" }, "AI agent failed")
        })
        events.emit(AgentSessionChange.StatusChanged(ws, true))
        advanceTimeBy(1000); runCurrent()
        val item = center.items.value.single()
        assertEquals(ActivityKind.AI_AGENT, item.kind)
        assertEquals(ActivityState.RUNNING, item.state)
        assertEquals("AI agent: Study", item.title)
        assertEquals(ws, workspaceIdOfAgentItem(item))
        assertNotNull(item.cancel).invoke()
        assertEquals(listOf(ws), stopped)
        events.emit(AgentSessionChange.LogUpdated(ws, AgentLogEntry.info("Working")))
        assertEquals("Working", center.items.value.single().message)
        events.emit(AgentSessionChange.UserDecisionPending(ws, true, "Bookmarks tool"))
        assertEquals("Waiting: Bookmarks tool", center.items.value.single().message)
        assertTrue(center.items.value.single().attention)
        assertEquals(1, center.unreadFailures.value)
        events.emit(AgentSessionChange.LogUpdated(ws, AgentLogEntry.info("Still working")))
        assertEquals("Waiting: Bookmarks tool", center.items.value.single().message)
        events.emit(AgentSessionChange.UserDecisionPending(ws, false))
        assertEquals("Still working", center.items.value.single().message)
        assertEquals(0, center.unreadFailures.value)
        events.emit(AgentSessionChange.StatusChanged(ws, false, AgentStopReason.COMPLETED))
        assertEquals(ActivityState.SUCCEEDED, center.items.value.single().state)
        source.close()
        events.emit(AgentSessionChange.StatusChanged(ws, true))
        assertEquals(ActivityState.SUCCEEDED, center.items.value.single().state)
    }

    @Test
    fun clearingStoppedSessionRemovesFailedActivity() = runTest {
        val center = ActivityCenter(backgroundScope) { currentTime }
        val ws = IdType()
        val source = AgentActivitySource(center, net.bible.service.llm.agent.AgentSessionManager.changes, {}, { null }, {
            AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
        })
        val session = net.bible.service.llm.agent.AgentSessionManager.getOrCreateSession(ws)
        try {
            assertTrue(session.tryStart(net.bible.service.llm.agent.AgentContext(promptId = IdType())))
            session.stop(reason = AgentStopReason.ERROR)
            assertEquals(ActivityState.FAILED, center.items.value.single().state)
            net.bible.service.llm.agent.AgentSessionManager.clearSession(ws)
            assertTrue(center.items.value.isEmpty())
        } finally {
            source.close()
            net.bible.service.llm.agent.AgentSessionManager.clearSession(ws)
        }
    }

    @Test
    fun errorsUseLastErrorAndNewRunsReplaceRows() = runTest {
        val events = EventSource<AgentSessionChange>()
        val center = ActivityCenter(backgroundScope) { currentTime }
        val a = IdType(); val b = IdType()
        val source = AgentActivitySource(center, events, {}, { null }, {
            AgentActivityStrings("AI agent", { it }, { it ?: "waiting" }, "AI agent failed")
        })
        events.emit(AgentSessionChange.StatusChanged(a, true))
        events.emit(AgentSessionChange.StatusChanged(b, true))
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, center.items.value.size)
        events.emit(AgentSessionChange.LogUpdated(a, AgentLogEntry.error("first error")))
        events.emit(AgentSessionChange.LogUpdated(a, AgentLogEntry.error("last error")))
        events.emit(AgentSessionChange.LogUpdated(a, AgentLogEntry.info("not an error")))
        events.emit(AgentSessionChange.StatusChanged(a, false, AgentStopReason.ERROR))
        assertEquals("last error", center.items.value.single { it.id == agentActivityId(a) }.message)
        assertEquals(1, center.unreadFailures.value)
        events.emit(AgentSessionChange.StatusChanged(a, true))
        assertEquals(0, center.unreadFailures.value)
        events.emit(AgentSessionChange.StatusChanged(a, false, AgentStopReason.ERROR))
        assertEquals("AI agent failed", center.items.value.single { it.id == agentActivityId(a) }.message)
        events.emit(AgentSessionChange.UserDecisionPending(b, true, null))
        events.emit(AgentSessionChange.StatusChanged(b, false, AgentStopReason.CANCELLED))
        assertEquals(1, center.unreadFailures.value)
        assertEquals(1, center.items.value.size)
        assertEquals(ActivityState.FAILED, center.items.value.single().state)
        center.dismiss(agentActivityId(a))
        assertTrue(center.items.value.isEmpty())
        assertEquals(0, center.unreadFailures.value)
        source.close()
    }
}
