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

package net.bible.service.db

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.SettingsDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room 3 close semantics the restore flow relies on (D1 Task 17): `closeForReplace` followed by the `closeAll`
 * of [DatabaseContainer.reset] closes a database twice, which must be harmless; and a closed Room 3 database never
 * reopens, so a DAO call in the window between the two fails instead of writing into whatever file is in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DatabaseContainerCloseTest {
    @Before fun setUp() = DatabaseContainer.reset()
    @After fun tearDown() = DatabaseContainer.reset()

    @Test fun closeForReplaceThenCloseAllIsSafeAndTheNextContainerWorks() {
        val old = DatabaseContainer.instance
        runBlocking { old.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "before close")) }

        old.closeForReplace(BookmarkDatabase.dbFileName)
        old.closeForReplace(SettingsDatabase.dbFileName)
        DatabaseContainer.reset() // closeAll(): closes both of them a second time

        val fresh = DatabaseContainer.instance
        assertNotSame(old, fresh)
        assertEquals(listOf("before close"),
            runBlocking { fresh.bookmarkDb.bookmarkDao().allLabelsSortedByName() }.map { it.name }.filter { it == "before close" })
    }

    @Test fun aDaoCallOnADatabaseClosedForReplaceFails() {
        val old = DatabaseContainer.instance
        old.closeForReplace(BookmarkDatabase.dbFileName)
        val error = runCatching { runBlocking { old.bookmarkDb.bookmarkDao().allLabelsSortedByName() } }.exceptionOrNull()
        assertNotNull("a closed Room 3 database must not reopen on a DAO call", error)
        // Room 3's RoomDatabase.throwIfClosed(); only a call racing the close sees SQLiteException(MISUSE) instead.
        assertTrue(error.toString(), error is IllegalStateException)
    }

    /**
     * A first cloud sync swaps the downloaded file in and calls resetXDb(), which replaces the instance. The container's
     * database lists must follow: sync()/vacuum() (run by every backup) must use the live instance, not throw on the
     * closed old one, and closeForReplace/closeAll must close the live one.
     */
    @Test fun aResetDatabaseIsTheOneSyncedVacuumedAndClosed() {
        val c = DatabaseContainer.instance
        val oldBookmarks = c.bookmarkDb
        val liveBookmarks = c.resetBookmarkDb()
        val liveWorkspaces = c.resetWorkspaceDb()
        assertNotSame(oldBookmarks, liveBookmarks)

        runBlocking { c.sync(); c.vacuum() } // threw "Connection pool is closed" on the stale instances

        c.closeForReplace(BookmarkDatabase.dbFileName)
        assertNotNull("closeForReplace must close the live bookmark database",
            runCatching { runBlocking { liveBookmarks.bookmarkDao().allLabelsSortedByName() } }.exceptionOrNull())

        DatabaseContainer.reset() // closeAll()
        assertNotNull("closeAll must close the live workspace database",
            runCatching { runBlocking { liveWorkspaces.workspaceDao().allWorkspaces() } }.exceptionOrNull())
    }
}
