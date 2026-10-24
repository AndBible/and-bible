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
package net.bible.service.device

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.crosswire.common.progress.JobManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ProgressNotificationManagerTest {
    @Test fun searchIndexJobsAreLeftToTheIndexingServiceNotification() {
        val manager = ProgressNotificationManager()
        manager.initialise()
        val index = JobManager.createJob("CREATE_INDEX-ZZ", "Creating index. Processing ZZ", null)
        val other = JobManager.createJob("INSTALL_BOOK-ZZ", "Installing ZZ", null)
        try {
            index.beginJob("x", 100); other.beginJob("x", 100)
            index.work = 10; other.work = 10
            assertEquals(listOf("INSTALL_BOOK-ZZ"), manager.progs.map { it.jobID })
        } finally {
            index.done(); other.done()
            manager.close()
        }
    }
}
