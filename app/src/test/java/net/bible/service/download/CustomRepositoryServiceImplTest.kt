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

package net.bible.service.download

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.download.CustomRepositoryData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A duplicate custom repository name is reported as `false`, not thrown. With the bundled SQLite driver (D1 Task 17)
 * the unique-index violation arrives as a plain `SQLiteException`, no longer the framework's
 * `SQLiteConstraintException` the service used to catch: before the fix this upsert crashed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CustomRepositoryServiceImplTest {
    @Before fun setUp() = DatabaseContainer.reset()
    @After fun tearDown() = DatabaseContainer.reset()

    @Test fun aDuplicateNameIsRejectedWithFalseOnTheProductionDriver() = runBlocking {
        val service = CustomRepositoryServiceImpl()
        val repo = CustomRepositoryData(name = "d1-dup-repo", type = "HTTPS", host = "example.org")
        assertTrue(service.upsert(repo))
        assertFalse(service.upsert(repo))
        assertEquals(1, service.list().count { it.name == "d1-dup-repo" })
    }

    @Test fun onlyConstraintErrorsCountAsViolations() {
        assertTrue(isConstraintViolation(SQLiteException("Error code: 2067, message: UNIQUE constraint failed: CustomRepository.name")))
        assertFalse(isConstraintViolation(SQLiteException("Error code: 26, message: file is not a database")))
    }
}
