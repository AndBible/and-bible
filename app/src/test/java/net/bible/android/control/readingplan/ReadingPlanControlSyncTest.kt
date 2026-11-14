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

package net.bible.android.control.readingplan

import android.os.Looper
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies that reading status changes arriving through sync are picked up by
 * [ReadingPlanControl], which keeps the current day's [ReadingStatus] cached in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingPlanControlSyncTest {
    private lateinit var control: ReadingPlanControl
    private val planCode = "y1ot1nt1_OTthenNT"
    private val day = 1

    @Before
    fun setUp() {
        control = BibleApplication.application.applicationComponent.readingPlanControl()
        control.setReadingPlan(planCode)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    /** Write a status for [day] straight into the db, as a sync download would. */
    private fun writeSyncedStatus(vararg readReadings: Int) {
        val remote = ReadingStatus(planCode, day, 2).apply {
            readReadings.forEach { setStatus(it, read = true, saveStatus = false) }
        }
        runBlocking {
            DatabaseContainer.instance.readingPlanDb.readingPlanDao()
                .addPlanStatus(planCode, day, remote.toString(), IdType())
        }
    }

    private fun postSyncEvent() {
        ABEventBus.post(ReadingPlansUpdatedViaSyncEvent(emptyList()))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun syncedStatusIsVisibleAfterSyncEvent() {
        assertFalse(control.getReadingStatus(day).isRead(1))

        writeSyncedStatus(1)
        postSyncEvent()

        assertTrue(control.getReadingStatus(day).isRead(1))
    }

    @Test
    fun syncedUnreadIsVisibleAfterSyncEvent() {
        writeSyncedStatus(1)
        postSyncEvent()
        assertTrue(control.getReadingStatus(day).isRead(1))

        // Another device marks the reading unread again
        writeSyncedStatus()
        postSyncEvent()

        assertFalse(control.getReadingStatus(day).isRead(1))
    }

    @Test
    fun cachedStatusIsKeptWithoutSyncEvent() {
        val cached = control.getReadingStatus(day)

        writeSyncedStatus(1)

        // No sync event: the same cached instance is returned (cache still works)
        assertTrue(cached === control.getReadingStatus(day))
    }
}
