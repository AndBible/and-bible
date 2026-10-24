/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.shareddata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.bible.service.db.DbTransactionMarker
import net.bible.service.db.isInDbTransactionOnThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins the per-platform semantics of [DbTransactionMarker]: on JVM/Android the mark follows the body across
 * dispatcher switches and never leaks to other threads; on iOS it is an empty context (see [markerTracksThread]).
 */
class DbTransactionMarkerContractTest {
    @Test
    fun noMarkOutsideAMarkedBody() = runTest {
        assertFalse(isInDbTransactionOnThread())
    }

    @Test
    fun markIsVisibleInsideAMarkedBody() = runTest {
        withContext(DbTransactionMarker) {
            assertEquals(markerTracksThread, isInDbTransactionOnThread())
        }
    }

    @Test
    fun markSurvivesADispatcherSwitchInsideTheBody() = runTest {
        withContext(DbTransactionMarker) {
            withContext(Dispatchers.Default) {
                assertEquals(markerTracksThread, isInDbTransactionOnThread())
            }
        }
    }

    @Test
    fun markIsRestoredAfterTheBodyAndDoesNotLeakAcrossThreads() = runTest {
        withContext(DbTransactionMarker) {
            withContext(DbTransactionMarker) {
                yield()
                withContext(Dispatchers.Default) {
                    assertEquals(markerTracksThread, isInDbTransactionOnThread())
                }
                assertEquals(markerTracksThread, isInDbTransactionOnThread())
            }
            // Leaving the inner body must not clear the outer mark.
            assertEquals(markerTracksThread, isInDbTransactionOnThread())
        }
        assertFalse(isInDbTransactionOnThread())

        // Best-effort smoke check only: an unrelated coroutine on Dispatchers.Default observes the mark state while
        // a marked body is suspended. It catches a leak only if the observer happens to run on the thread the
        // suspended body left; it does not prove a leak is impossible.
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val observed = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Default) {
            val marked = async {
                withContext(DbTransactionMarker) {
                    started.complete(Unit)
                    release.await()
                }
            }
            started.await()
            CoroutineScope(Dispatchers.Default).async { observed.complete(isInDbTransactionOnThread()) }.await()
            release.complete(Unit)
            marked.await()
        }
        assertFalse(observed.await())
    }
}
