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
