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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F123: the agent service may stop only when the last run it launched has ended. */
class LiveRunsTest {
    @Test fun aLoserEndingFirstDoesNotLetTheServiceStop() {
        val runs = LiveRuns()
        val winner = Job(); val loser = Job()
        runs.add(winner); runs.add(loser)
        assertFalse("the winner is still live", runs.finish(loser))
        assertTrue("the last live run ends", runs.finish(winner))
    }

    @Test fun aSingleRunThatEndsLetsTheServiceStop() {
        val runs = LiveRuns()
        val only = Job()
        runs.add(only)
        assertTrue(runs.finish(only))
    }

    @Test fun finishingTwiceDoesNotReviveOrUnderflow() {
        val runs = LiveRuns()
        val a = Job(); val b = Job()
        runs.add(a); runs.add(b)
        assertFalse(runs.finish(a))
        assertFalse("a repeated finish of a must not count b as ended", runs.finish(a))
        assertTrue(runs.finish(b))
    }

    @Test fun concurrentAddFinishKeepsCount() {
        val runs = LiveRuns()
        val threads = 8
        val perThread = 10_000
        val emptied = java.util.concurrent.atomic.AtomicInteger(0)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val start = java.util.concurrent.CountDownLatch(1)
        val workers = (0 until threads).map {
            Thread {
                start.await()
                try {
                    repeat(perThread) {
                        val job = Job()
                        runs.add(job)
                        if (runs.finish(job)) emptied.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }.also { it.start() }
        }
        start.countDown()
        workers.forEach { it.join() }
        assertTrue("worker threads threw: $errors", errors.isEmpty())
        val last = Job()
        runs.add(last)
        assertTrue("the set ends empty after the last finish", runs.finish(last))
        assertTrue("finish returned true at least once (the set emptied)", emptied.get() >= 1)
        assertTrue("a finish of an unknown job on an empty set reports empty", runs.finish(Job()))
    }
}
