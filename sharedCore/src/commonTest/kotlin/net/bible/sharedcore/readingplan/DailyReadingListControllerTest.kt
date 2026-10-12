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
package net.bible.sharedcore.readingplan

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DailyReadingListControllerTest {
    private val sample = listOf(DayEntry(1, "Day 1", "Gen 1-2; Matt 1"), DayEntry(2, "Day 2", "Gen 3-4; Matt 2"))

    @Test fun load_populates_days() = runTest {
        val c = DailyReadingListController(this, { sample }, {})
        assertEquals(sample, c.days.value)
        assertNull(c.error.value)
    }

    @Test fun select_forwards_day() = runTest {
        var picked = -1
        val c = DailyReadingListController(this, { sample }, { picked = it })
        c.select(2)
        assertEquals(2, picked)
    }

    @Test fun throwing_load_sets_error() = runTest {
        val c = DailyReadingListController(this, { throw RuntimeException("boom") }, {})
        assertEquals(ReadingPlanError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }

    @Test fun suspended_source_keeps_rows_empty_until_completion() = runTest {
        val gate = CompletableDeferred<List<DayEntry>>()
        val c = DailyReadingListController(this, { gate.await() }, {})
        assertEquals(emptyList(), c.days.value)
        gate.complete(sample)
        runCurrent()
        assertEquals(sample, c.days.value)
    }

    @Test fun cancelled_source_does_not_set_failed() = runTest {
        val c = DailyReadingListController(this, { throw CancellationException("screen closed") }, {})
        assertNull(c.error.value)
    }

    @Test fun latest_reload_rejects_old_success_and_error() = runTest {
        for (fail in listOf(false, true)) {
            val held = CompletableDeferred<Unit>()
            var calls = 0
            val c = DailyReadingListController(this, {
                if (++calls == 1) {
                    withContext(NonCancellable) { held.await() }
                    if (fail) error("obsolete")
                    emptyList()
                } else sample
            }, {})
            c.load()
            assertEquals(sample, c.days.value)
            held.complete(Unit)
            runCurrent()
            assertEquals(sample, c.days.value)
            assertNull(c.error.value)
        }
    }
}
