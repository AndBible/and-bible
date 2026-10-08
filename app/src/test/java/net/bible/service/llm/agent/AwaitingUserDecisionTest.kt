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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.AppPosition
import net.bible.android.view.activity.base.CurrentActivityHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/** [AgentSessionManager.awaitingUserDecision]: app background/foreground while a user dialog blocks the agent. */
class AwaitingUserDecisionTest {
    private val ws = IdType()
    private val seen = mutableListOf<AgentSessionChange>()
    private var sub: net.bible.sharedcore.event.Subscription? = null

    private var savedActivities: List<ActivityBase> = emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    private fun setCurrentActivity(present: Boolean) {
        holderActivities().clear()
        if (present) holderActivities().add(mock(ActivityBase::class.java))
    }

    @Before fun subscribe() {
        savedActivities = ArrayList(holderActivities())
        setCurrentActivity(true)
        AgentSessionManager.resetSubscribersForTest()
        CurrentActivityHolder.resetSubscribersForTest()
        sub = AgentSessionManager.changes.subscribe { seen += it }
    }

    @After fun cleanup() {
        holderActivities().clear()
        holderActivities().addAll(savedActivities)
        sub?.cancel()
        AgentSessionManager.resetSubscribersForTest()
        CurrentActivityHolder.resetSubscribersForTest()
    }

    private fun waiting(w: Boolean, tool: String? = null) = AgentSessionChange.PermissionWaiting(ws, w, tool)

    @Test fun backgroundThenForegroundMirrorsIntoPermissionWaiting() = runTest {
        val gate = CompletableDeferred<String>()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            AgentSessionManager.awaitingUserDecision(ws, "Tool X") { gate.await() }
        }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        assertEquals(listOf<AgentSessionChange>(waiting(true, "Tool X")), seen)
        CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND)
        assertEquals(listOf<AgentSessionChange>(waiting(true, "Tool X"), waiting(false)), seen)
        gate.complete("ok")
        assertEquals("ok", job.await())
        assertEquals(2, seen.size)
    }

    @Test fun foregroundWithoutPriorBackgroundEmitsNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await() } }
        CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND)
        gate.complete(Unit); job.await()
        assertEquals(emptyList<AgentSessionChange>(), seen)
    }

    @Test fun endingTheBlockWhileBackgroundedEmitsFalseExactlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await() } }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        gate.complete(Unit); job.await()
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T"), waiting(false)), seen)
    }

    @Test fun anExceptionFromTheBlockStillEmitsFalseAndPropagates() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await(); error("boom") } }
        }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        gate.complete(Unit)
        assertEquals("boom", job.await().exceptionOrNull()?.message)
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T"), waiting(false)), seen)
    }

    @Test fun cancellationEmitsNothingExtra() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await() } }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        job.cancelAndJoin()
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T")), seen)
    }

    @Test fun noEmissionsAfterTheBlockEnded() = runTest {
        AgentSessionManager.awaitingUserDecision(ws, "T") { }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND)
        assertEquals(emptyList<AgentSessionChange>(), seen)
    }

    @Test fun nullWorkspaceEmitsNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(null, "T") { gate.await() } }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        gate.complete(Unit); job.await()
        assertEquals(emptyList<AgentSessionChange>(), seen)
    }

    @Test fun aRepeatedBackgroundEmitsOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await() } }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T")), seen)
        gate.complete(Unit); job.await()
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T"), waiting(false)), seen)
    }

    @Test fun backgroundForegroundBackgroundThenEndEmitsTrueFalseTrueFalse() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await() } }
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND)
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        gate.complete(Unit); job.await()
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T"), waiting(false), waiting(true, "T"), waiting(false)), seen)
    }

    @Test fun noCurrentActivityAtSubscriptionAnnouncesAtOnceAndClosesOnce() = runTest {
        setCurrentActivity(false)
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AgentSessionManager.awaitingUserDecision(ws, "T") { gate.await() } }
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T")), seen)
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND) // already announced: no duplicate
        assertEquals(1, seen.size)
        gate.complete(Unit); job.await()
        assertEquals(listOf<AgentSessionChange>(waiting(true, "T"), waiting(false)), seen)
    }
}
