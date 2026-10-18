/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundSyncPolicyTest {
    private var started = 0
    private var signInCalls = 0

    private fun policy(
        enabled: Boolean = true,
        replacing: Boolean = false,
        interval: Long = 300,
        last: Long = 0,
        now: Long = 1_000_000,
        signedIn: Boolean = true,
        signIn: Boolean? = true,
        result: Boolean = true,
    ) = BackgroundSyncPolicy(
        syncEnabled = { enabled },
        replacing = { replacing },
        intervalSeconds = { interval },
        lastSynchronizedMs = { last },
        nowMs = { now },
        signedIn = { signedIn },
        signIn = { signInCalls++; signIn },
        startSync = { started++; CompletableDeferred(result) },
    )

    @Test
    fun disabledSyncCompletesWithoutStarting() = runTest {
        assertTrue(policy(enabled = false).runOnce())
        assertEquals(0, started)
    }

    @Test
    fun replacingSkips() = runTest {
        assertTrue(policy(replacing = true).runOnce())
        assertEquals(0, started)
    }

    @Test
    fun withinIntervalSkips() = runTest {
        assertTrue(policy(last = 900_000, now = 1_000_000, interval = 300).runOnce())
        assertEquals(0, started)
    }

    @Test
    fun dueSyncSignsInAndStarts() = runTest {
        assertFalse(policy(signedIn = false, signIn = true, result = false).runOnce())
        assertEquals(1, signInCalls)
        assertEquals(1, started)
    }

    @Test
    fun dueSyncWhenSignedInSkipsSignIn() = runTest {
        assertTrue(policy().runOnce())
        assertEquals(0, signInCalls)
        assertEquals(1, started)
    }

    @Test
    fun signInFailureDoesNotStart() = runTest {
        assertFalse(policy(signedIn = false, signIn = false).runOnce())
        assertFalse(policy(signedIn = false, signIn = null).runOnce())
        assertEquals(0, started)
    }

    @Test
    fun nextEarliestSecondsFloorsAtSixty() {
        assertEquals(300, policy(interval = 300).nextEarliestSeconds())
        assertEquals(60, policy(interval = 10).nextEarliestSeconds())
    }
}
