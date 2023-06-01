/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.android

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseCategory

import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseDefinition
import net.bible.service.db.DatabasePatching
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SmallTest
class DatabasePatchingTests {
    @Before
    fun setUp() {
        DatabaseContainer.ready = true
        DatabaseContainer.instance
    }

    private fun getDbDef(dbFile1: File): DatabaseDefinition<BookmarkDatabase> {
        var bmarkDb = DatabaseContainer.instance.getBookmarkDb(dbFile1.absolutePath)
        val dbDef = DatabaseDefinition(
            bmarkDb,
            {DatabaseContainer.instance.getBookmarkDb(it)},
            {
                bmarkDb.close()
                bmarkDb = DatabaseContainer.instance.getBookmarkDb(dbFile1.absolutePath)
                bmarkDb
            },
            dbFile1,
            DatabaseCategory.BOOKMARKS,
            deviceId = UUID.randomUUID().toString(),
        )
        DatabasePatching.createTriggers(dbDef)
        return dbDef
    }

    @Test
    fun testSimplePatchFileWritingAndReading() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        assertThat(dbDef1.localDb.syncDao().allLogEntries().size, equalTo(2))

        val patchFile = DatabasePatching.createPatchForDatabase(dbDef1)!!
        DatabasePatching.applyPatchesForDatabase(dbDef2, patchFile)
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(DatabasePatching.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabasePatching.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testMergingChanges() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 3"))
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 4"))

        val patchFile1 = DatabasePatching.createPatchForDatabase(dbDef1)!!
        val patchFile2 = DatabasePatching.createPatchForDatabase(dbDef2)!!

        DatabasePatching.applyPatchesForDatabase(dbDef2, patchFile1)
        DatabasePatching.applyPatchesForDatabase(dbDef1, patchFile2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(4))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(4))

        assertThat(DatabasePatching.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabasePatching.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testBasicDeletion() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = DatabasePatching.createPatchForDatabase(dbDef1)!!
        DatabasePatching.applyPatchesForDatabase(dbDef2, patchFile1)

        dbDef2.localDb.bookmarkDao().delete(label1)
        val patchFile2 = DatabasePatching.createPatchForDatabase(dbDef2)!!

        DatabasePatching.applyPatchesForDatabase(dbDef1, patchFile2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(1))

        assertThat(DatabasePatching.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabasePatching.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testBasicUpdate() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = DatabasePatching.createPatchForDatabase(dbDef1)!!
        DatabasePatching.applyPatchesForDatabase(dbDef2, patchFile1)

        val label1mod = label1.copy()
        label1mod.name = "label 1 mod"
        dbDef2.localDb.bookmarkDao().update(label1mod)
        val patchFile2 = DatabasePatching.createPatchForDatabase(dbDef2)!!

        DatabasePatching.applyPatchesForDatabase(dbDef1, patchFile2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))

        assertThat(dbDef1.localDb.bookmarkDao().labelById(label1.id)?.name, equalTo("label 1 mod"));

        assertThat(DatabasePatching.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabasePatching.createPatchForDatabase(dbDef2), equalTo(null))
    }
}
