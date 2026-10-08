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

import kotlinx.coroutines.Job
import net.bible.android.database.IdType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** F122: two concurrent starts of one session must not both win. */
class AgentSessionTryStartTest {
    private fun ctx() = AgentContext(promptId = IdType())

    @Test fun onlyOneOfTwoConcurrentStartsWins() {
        repeat(200) {
            val session = AgentSession(IdType())
            val barrier = CyclicBarrier(2)
            val wins = AtomicInteger()
            val ts = List(2) { thread { barrier.await(); if (session.tryStart(ctx())) wins.incrementAndGet() } }
            ts.forEach { it.join() }
            assertEquals(1, wins.get())
            assertTrue(session.isRunning)
        }
    }

    @Test fun aStoppedSessionCanStartAgain() {
        val session = AgentSession(IdType())
        assertTrue(session.tryStart(ctx()))
        assertFalse(session.tryStart(ctx()))
        session.stop()
        assertTrue(session.tryStart(ctx()))
    }

    /** F123: the winner's job is bound at the moment it wins; a losing start must not replace it. */
    @Test fun aLosingStartDoesNotReplaceTheWinnersJob() {
        val session = AgentSession(IdType())
        val winner = Job()
        val loser = Job()
        assertTrue(session.tryStart(ctx(), winner))
        assertFalse(session.tryStart(ctx(), loser))
        assertTrue("the winner's job must stay bound", session.job === winner)
        session.stop()
        assertTrue("stop() reaches the winner", winner.isCancelled)
        assertFalse("the loser is not the session's to cancel", loser.isCancelled)
    }

    /** F134: stop() must not unbind a new winner's job that started while it was cancelling the old one. */
    @Test fun stopNeverUnbindsAWinnerThatStartsDuringTheCancel() {
        val session = AgentSession(IdType())
        val old = Job()
        val next = Job()
        assertTrue(session.tryStart(ctx(), old))
        var restarted = false
        old.invokeOnCompletion { restarted = session.tryStart(ctx(), next) }
        session.stop()
        assertTrue("a start that won during stop() must keep its job bound", !restarted || session.job === next)
    }

    @Test fun theJobIsBoundBeforeTheStartIsAnnounced() {
        val winner = Job()
        var jobSeenAtStart: Job? = null
        lateinit var session: AgentSession
        session = AgentSession(IdType(), emit = { if (it is AgentSessionChange.StatusChanged && it.isRunning) jobSeenAtStart = session.job })
        assertTrue(session.tryStart(ctx(), winner))
        assertTrue(jobSeenAtStart === winner)
    }

    /** Review Focus 4 (C2): a cached result opens even while a run is going, so the cache check stays first. */
    @Test fun aCachedResultStillOpensWhileARunIsGoing() {
        val src = File("src/main/java/net/bible/service/llm/agent/AgentSessionManager.kt").readText()
        val body = src.substringAfter("suspend fun executePrompt(").substringBefore("fun findCachedPage")
        val cache = body.indexOf("findCachedPage(prompt")
        val start = body.indexOf("session.tryStart(")
        assertTrue("cache check $cache must precede tryStart $start", cache in 0 until start)
        assertFalse("no check-then-start left before tryStart", Regex("""if\s*\(\s*session\.isRunning\s*\)""").containsMatchIn(body.substring(0, start)))
    }
}
