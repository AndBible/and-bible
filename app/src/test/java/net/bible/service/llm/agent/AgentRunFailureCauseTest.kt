/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.llm.agent

import android.os.Looper
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.LlmUsage
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityState
import net.bible.android.control.activity.AgentActivitySource
import net.bible.android.control.activity.AgentActivityStrings
import net.bible.android.database.IdType
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * The last hop of an agent failure: an [AgentEvent.Error] or an unexpected exception while collecting the executor
 * flow must reach the activity row as its `cause`, and only a real cause makes the row reportable. Runs the real
 * collection ([AgentSessionManager.collectAgentRun]) with a fake event flow; same Main-looper idling as
 * [ExecutePromptLostRaceTest] (runTest would deadlock).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AgentRunFailureCauseTest {
    private val ws = IdType()
    private val centerScope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val center = ActivityCenter(centerScope) { 0L }
    private val source = AgentActivitySource(center, AgentSessionManager.changes, {}, { null }, {
        AgentActivityStrings("Agent", { it }, { "waiting" }, "failed")
    })

    @After fun tearDown() {
        source.close()
        AgentSessionManager.clearSession(ws)
        centerScope.coroutineContext[Job]?.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        DatabaseResetter.resetDatabase()
    }

    /** Starts a session for [ws] inside the coroutine (so its job owns it) and collects [events]; returns what the collection threw. */
    private fun run(events: Flow<AgentEvent>): Throwable? {
        val thrown = AtomicReference<Throwable?>()
        val d = CoroutineScope(Dispatchers.IO).async {
            val job = currentCoroutineContext()[Job]
            val session = AgentSessionManager.getOrCreateSession(ws)
            val context = AgentContext(promptId = IdType())
            assertTrue(session.tryStart(context, job))
            try {
                AgentSessionManager.collectAgentRun(
                    events, session, net.bible.service.llm.AgentPrompt(name = "t"), context,
                    CacheableContext.fromAgentContext(context), atomic(false), null, job
                )
            } catch (e: Throwable) {
                thrown.set(e)
            }
        }
        awaitCompletion(d)
        return thrown.get()
    }

    private fun awaitCompletion(run: Deferred<*>) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!run.isCompleted) {
            check(System.currentTimeMillis() < deadline) { "collectAgentRun did not return in 30 s" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    /** Uses real Room pricing/provider lookups: completion must not cancel those suspensions. */
    private fun completedRun(): Deferred<Unit> {
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "Completion fixture")
        val model = LlmConfiguredModel(
            providerConfigId = provider.id, modelId = "completion-fixture",
            inputPricePerMillion = 2.0, outputPricePerMillion = 4.0,
        )
        runBlocking {
            val db = DatabaseContainer.instance.aiSettingsDb
            db.llmProviderConfigDao().insert(provider)
            db.llmConfiguredModelDao().insert(model)
        }
        return CoroutineScope(Dispatchers.IO).async {
            val job = currentCoroutineContext()[Job]!!
            val prompt = AgentPrompt(name = "Successful completion")
            val context = AgentContext(promptId = prompt.id)
            val session = AgentSessionManager.getOrCreateSession(ws)
            assertTrue(session.tryStart(context, job))
            val usage = LlmUsage(inputTokens = 1000, outputTokens = 500)
            session.rawLlmLog!!.apply {
                addMessage("assistant", "Synthetic successful raw response")
                addUsageForIteration(1, usage, model.modelId, model.id)
            }
            AgentSessionManager.collectAgentRun(
                flowOf(AgentEvent.CompletedWithoutDocument(
                    "Finished", totalIterations = 1, usage = usage,
                    model = model.modelId, configuredModelId = model.id,
                )),
                session, prompt, context, CacheableContext.fromAgentContext(context),
                atomic(false), null, job,
            )
            assertTrue("completion must leave its owning caller active", job.isActive)
        }
    }

    @Test fun successfulCompletionPersistsHistoryAndTotalCostBeforeTerminalStatus() {
        var countAtTerminal: Int? = null
        var totalCostAtTerminal = false
        var restartDuringCostUpdate: Boolean? = null
        val subscription = AgentSessionManager.changes.subscribe { change ->
            if (change.workspaceId == ws) {
                val session = AgentSessionManager.getSession(ws)!!
                if (change is AgentSessionChange.LogUpdated && change.entry.isTotalCost) {
                    restartDuringCostUpdate = session.tryStart(AgentContext(promptId = IdType()))
                }
                if (change is AgentSessionChange.StatusChanged && change.stopReason == AgentStopReason.COMPLETED) {
                    countAtTerminal = runBlocking {
                        DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getCount()
                    }
                    totalCostAtTerminal = session.logEntries.last().isTotalCost
                }
            }
        }
        try {
            val run = completedRun()
            awaitCompletion(run)
            val records = runBlocking {
                val dao = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao()
                dao.allSummaries().map { dao.getById(it.id)!! }
            }
            assertEquals("one successful history row must survive completion", 1, records.size)
            val record = records.single()
            assertFalse(record.wasError)
            assertEquals("CUSTOM", record.providerType)
            assertEquals("completion-fixture", record.modelName)
            assertNotNull(record.configuredModelId)
            assertEquals(1000L, record.totalInputTokens)
            assertEquals(500L, record.totalOutputTokens)
            assertEquals(0.004, record.estimatedCostUsd, 0.000001)
            assertEquals(1, record.iterationCount)
            assertTrue(RawLlmLog.gzipDecompress(record.logData).contains("Synthetic successful raw response"))
            assertEquals("history must exist before terminal notification", 1, countAtTerminal)
            assertTrue("total cost must exist on the terminal log entry before notification", totalCostAtTerminal)
            assertEquals("a new run must not replace rawLlmLog during finalization", false, restartDuringCostUpdate)
        } finally {
            subscription.cancel()
        }
    }

    @Test fun successfulCompletionReturnsNormallyWithoutCancellingItsOwningCoroutine() {
        val run = completedRun()
        awaitCompletion(run)
        assertFalse("successful completion must not cancel its owning coroutine", run.isCancelled)
        runBlocking { run.await() }
        val session = AgentSessionManager.getSession(ws)!!
        assertFalse(session.isRunning)
        assertEquals(null, session.job)
        assertTrue("completion must release the start gate", session.tryStart(AgentContext(promptId = IdType())))
    }

    @Test fun anErrorEventsCauseReachesTheReportableActivityItem() {
        val cause = IllegalStateException("provider exploded")
        run(flowOf(AgentEvent.Error("It failed", cause)))
        val item = center.items.value.single()
        assertEquals(ActivityState.FAILED, item.state)
        assertSame("cause must travel from the Error event to the row", cause, item.cause)
        assertTrue(item.reportable)
    }

    @Test fun anErrorEventWithoutACauseIsNotReportable() {
        run(flowOf(AgentEvent.Error("Reached the iteration limit")))
        val item = center.items.value.single()
        assertEquals(ActivityState.FAILED, item.state)
        assertEquals(null, item.cause)
        assertFalse("max-iterations and parse errors are not bugs", item.reportable)
    }

    @Test fun anUnexpectedExceptionStopsTheSessionAsErrorAndIsRethrown() {
        val boom = IllegalStateException("db write failed")
        val thrown = run(flow { throw boom })
        assertSame(boom, thrown)
        assertFalse("the session must not stay running", AgentSessionManager.getSession(ws)!!.isRunning)
        val item = center.items.value.single()
        assertEquals("the row must not spin forever", ActivityState.FAILED, item.state)
        assertSame(boom, item.cause)
        assertTrue(item.reportable)
        assertNotNull(item.message ?: "failed")
    }
}
