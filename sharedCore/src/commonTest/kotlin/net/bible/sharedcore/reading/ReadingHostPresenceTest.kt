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
package net.bible.sharedcore.reading

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading-host re-typing R7b. [ReadingHostPresence] is two fields' worth of state, and every one of
 * its properties is load-bearing somewhere it cannot be seen: [ReadingViewVisibility] and
 * [ReadingViewHostCallbacks] both resolve "is this reading view on screen?" through it, and both
 * answer silently.
 *
 * `ReadingViewVisibilityTest` covers what the two seams DO with the answer; this class covers the
 * answer itself, and in particular the asymmetry between declaring and retracting a presence, which
 * is the one part a caller cannot get right by symmetry.
 */
class ReadingHostPresenceTest {

    @BeforeTest
    fun reset() = ReadingHostPresence.setForeground(null)

    @AfterTest
    fun clear() = ReadingHostPresence.setForeground(null)

    /**
     * **The asymmetry, and the whole reason the token exists.** Both hosts call
     * `setForeground(this)` from `onResume` and retract from `onPause` — but Android's lifecycle
     * overlaps hosts, and a stale pause (a paused-then-resumed host, a `MULTIPLE_TASK` second
     * instance, an out-of-order callback) can arrive after another host has already taken the
     * front. A bare `setForeground(null)` in `onPause` would then blank the presence of the host
     * the user IS looking at, and with it the reading view's history and its key handlers, for as
     * long as it lasted. Mutation: make `clearForeground` write `foreground = null`
     * unconditionally; the last assertion fails.
     */
    @Test
    fun aStalePauseDoesNotClearTheHostThatCameToTheFrontAfterIt() {
        val first = Any()
        val second = Any()

        ReadingHostPresence.setForeground(first)
        assertTrue(ReadingHostPresence.isForeground(first))

        // The second host comes to the front…
        ReadingHostPresence.setForeground(second)
        assertFalse(ReadingHostPresence.isForeground(first), "only one host is in front at a time")

        // …and only now does the first host's onPause arrive.
        ReadingHostPresence.clearForeground(first)
        assertTrue(
            ReadingHostPresence.isForeground(second),
            "a stale pause must retract only its OWN presence — the host in front keeps its own",
        )
    }

    /** A host retracting its own presence while it IS in front really does retract it. */
    @Test
    fun aHostsOwnPauseRetractsItsOwnPresence() {
        val host = Any()
        ReadingHostPresence.setForeground(host)
        ReadingHostPresence.clearForeground(host)
        assertFalse(ReadingHostPresence.isForeground(host), "the host that paused is no longer in front")
    }

    /**
     * Identity, not equality, and never `null`. The token is an Activity: two instances of one
     * Activity class are two different hosts (`FLAG_ACTIVITY_MULTIPLE_TASK` makes that real), and a
     * `data class`-like equality would merge them. A null host is never foreground, so an unowned
     * publication cannot pass the gate by accident — including when nothing is in front at all,
     * where `foreground` itself is null. Mutation: write `isForeground` as `host == foreground` and
     * both assertions fail.
     */
    @Test
    fun presenceIsIdentityAndNullIsNeverForeground() {
        val a = ValueLikeHost("x")
        val b = ValueLikeHost("x")
        ReadingHostPresence.setForeground(a)
        assertFalse(ReadingHostPresence.isForeground(b), "an EQUAL host is not the SAME host")

        ReadingHostPresence.setForeground(null)
        assertFalse(ReadingHostPresence.isForeground(null), "nothing in front, nothing is foreground")
    }

    private data class ValueLikeHost(val name: String)
}
