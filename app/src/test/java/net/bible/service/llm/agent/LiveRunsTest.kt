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
}
