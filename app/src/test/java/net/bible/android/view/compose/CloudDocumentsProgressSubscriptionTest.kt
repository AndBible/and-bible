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

package net.bible.android.view.compose

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.view.activity.nav.subscribeToDocumentSyncRunning
import net.bible.sharedcore.event.EventSource
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudDocumentsProgressSubscriptionTest {

    @Test
    fun aFastDrainStillDeliversBothEdgesInOrder() = runTest {
        val source = EventSource<Boolean>()
        val seen = mutableListOf<Boolean>()
        val stop = subscribeToDocumentSyncRunning(backgroundScope, source) { seen += it }
        runCurrent()
        source.emit(true)
        source.emit(false) // both before the collector runs
        assertEquals("collector has not run yet", emptyList<Boolean>(), seen)
        runCurrent()
        assertEquals(listOf(true, false), seen)
        stop()
        runCurrent()
        source.emit(true)
        runCurrent()
        assertEquals("nothing after stop", listOf(true, false), seen)
    }
}
