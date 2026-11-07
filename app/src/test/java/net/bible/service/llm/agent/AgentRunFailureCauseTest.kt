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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
        val deadline = System.currentTimeMillis() + 30_000
        while (!d.isCompleted) {
            check(System.currentTimeMillis() < deadline) { "collectAgentRun did not return in 30 s" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return thrown.get()
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
