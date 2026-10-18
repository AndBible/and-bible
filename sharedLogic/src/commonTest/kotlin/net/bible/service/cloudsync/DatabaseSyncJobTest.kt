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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseSyncJobTest {
    private val calls = mutableListOf<String>()

    private suspend fun runJob(synchronize: suspend () -> Unit = { calls += "synchronize" }) = runDatabaseSyncJob(
        notifyRunning = { calls += "running=$it" },
        synchronize = synchronize,
        waitUntilFinished = { calls += "wait" },
        recordFinished = { calls += "record" },
    )

    @Test
    fun successRecordsFinished() = runTest {
        assertTrue(runJob())
        assertEquals(listOf("running=true", "synchronize", "wait", "record"), calls)
    }

    @Test
    fun failureClearsRunning() = runTest {
        assertFalse(runJob { throw IllegalStateException("boom") })
        assertEquals(listOf("running=true", "running=false"), calls)
    }

    @Test
    fun cancellationClearsRunningAndRethrows() = runTest {
        assertFailsWith<CancellationException> { runJob { throw CancellationException("cancelled") } }
        assertEquals(listOf("running=true", "running=false"), calls)
    }
}
