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
import net.bible.service.devicesync.DatabaseCategory

import net.bible.service.db.DatabaseContainer
import net.bible.service.devicesync.SyncableDatabaseDefinition
import net.bible.service.devicesync.DatabaseSync
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

infix fun <E, T : Iterable<E>> T?.mustEqualTo(theOther: Iterable<E>) = Assert.assertEquals(theOther, this)

@RunWith(AndroidJUnit4::class)
@SmallTest
class DatabasePatchingTests {
    @Before
    fun setUp() {
        DatabaseContainer.ready = true
        DatabaseContainer.instance
    }

    private fun getDbDef(dbFile1: File): SyncableDatabaseDefinition<BookmarkDatabase> {
        var bmarkDb = DatabaseContainer.instance.getBookmarkDb(dbFile1.absolutePath)
        val dbDef = SyncableDatabaseDefinition(
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
        DatabaseSync.createTriggers(dbDef)
        return dbDef
    }

    private fun sync(dbDef1: SyncableDatabaseDefinition<*>, dbDef2: SyncableDatabaseDefinition<*>) {
        val patch1 = DatabaseSync.createPatchForDatabase(dbDef1)
        val patch2 = DatabaseSync.createPatchForDatabase(dbDef2)
        DatabaseSync.applyPatchesForDatabase(dbDef1, patch2)
        DatabaseSync.applyPatchesForDatabase(dbDef2, patch1)
        checkLog(dbDef1, dbDef2)
    }

    private fun sync3(dbDef1: SyncableDatabaseDefinition<*>, dbDef2: SyncableDatabaseDefinition<*>, dbDef3: SyncableDatabaseDefinition<*>) {
        val patch1 = DatabaseSync.createPatchForDatabase(dbDef1)
        val patch2 = DatabaseSync.createPatchForDatabase(dbDef2)
        val patch3 = DatabaseSync.createPatchForDatabase(dbDef3)
        DatabaseSync.applyPatchesForDatabase(dbDef1, patch2, patch3)
        DatabaseSync.applyPatchesForDatabase(dbDef2, patch1, patch3)
        DatabaseSync.applyPatchesForDatabase(dbDef3, patch1, patch2)
        checkLog(dbDef1, dbDef2)
        checkLog(dbDef2, dbDef3)
    }

    private fun checkLog(dbDef1: SyncableDatabaseDefinition<*>, dbDef2: SyncableDatabaseDefinition<*>) {
        dbDef1.dao.allLogEntries() mustEqualTo dbDef2.dao.allLogEntries()
    }

    @Test
    fun testSimplePatchFileWritingAndReading() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        assertThat(dbDef1.localDb.syncDao().allLogEntries().size, equalTo(2))

        val patchFile = DatabaseSync.createPatchForDatabase(dbDef1)!!
        DatabaseSync.applyPatchesForDatabase(dbDef2, patchFile)
        checkLog(dbDef1, dbDef2)
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(DatabaseSync.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabaseSync.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testMergingChanges() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 3"))
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 4"))

        sync(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(4))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(4))

        assertThat(DatabaseSync.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabaseSync.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testBasicDeletion() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = DatabaseSync.createPatchForDatabase(dbDef1)!!
        DatabaseSync.applyPatchesForDatabase(dbDef2, patchFile1)
        checkLog(dbDef1, dbDef2)

        dbDef2.localDb.bookmarkDao().delete(label1)
        val patchFile2 = DatabaseSync.createPatchForDatabase(dbDef2)!!
        DatabaseSync.applyPatchesForDatabase(dbDef1, patchFile2)
        checkLog(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(1))

        assertThat(DatabaseSync.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabaseSync.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testBasicUpdate() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = DatabaseSync.createPatchForDatabase(dbDef1)!!
        DatabaseSync.applyPatchesForDatabase(dbDef2, patchFile1)
        checkLog(dbDef1, dbDef2)

        val label1mod = label1.copy()
        label1mod.name = "label 1 mod"
        dbDef2.localDb.bookmarkDao().update(label1mod)
        val patchFile2 = DatabaseSync.createPatchForDatabase(dbDef2)!!
        DatabaseSync.applyPatchesForDatabase(dbDef1, patchFile2)
        checkLog(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))

        assertThat(dbDef1.localDb.bookmarkDao().labelById(label1.id)?.name, equalTo("label 1 mod"));

        assertThat(DatabaseSync.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabaseSync.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testSimultaneousUpdate() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = DatabaseSync.createPatchForDatabase(dbDef1)!!
        DatabaseSync.applyPatchesForDatabase(dbDef2, patchFile1)
        checkLog(dbDef1, dbDef2)

        val label1mod1 = label1.copy()
        val label1mod2 = label1.copy()
        label1mod1.name = "label 1 mod 1"
        label1mod2.name = "label 1 mod 2"
        dbDef2.localDb.bookmarkDao().update(label1mod1)
        dbDef1.localDb.bookmarkDao().update(label1mod2)

        sync(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))

        assertThat(dbDef1.localDb.bookmarkDao().labelById(label1.id)?.name, equalTo("label 1 mod 2"));
        assertThat(dbDef2.localDb.bookmarkDao().labelById(label1.id)?.name, equalTo("label 1 mod 2"));

        assertThat(DatabaseSync.createPatchForDatabase(dbDef1), equalTo(null))
        assertThat(DatabaseSync.createPatchForDatabase(dbDef2), equalTo(null))
    }

    @Test
    fun testBookmarkToLabelUpdates() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.Bookmark()
        val bookmark2 = BookmarkEntities.Bookmark()
        val bl1 = BookmarkEntities.BookmarkToLabel(bookmark1, label1)
        dbDef1.localDb.bookmarkDao().insert(bookmark1)
        dbDef2.localDb.bookmarkDao().insert(bookmark2)
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(bl1)

        val patchFile1 = DatabaseSync.createPatchForDatabase(dbDef1)!!
        DatabaseSync.applyPatchesForDatabase(dbDef2, patchFile1)
        val bl2 = BookmarkEntities.BookmarkToLabel(bookmark2, label1)
        dbDef2.localDb.bookmarkDao().insert(bl2)
        dbDef1.localDb.bookmarkDao().delete(label1)

        assertThat(dbDef1.dao.findLogEntries("Bookmark", "UPSERT").size, equalTo(1))
        assertThat(dbDef1.dao.findLogEntries("Label", "UPSERT").size, equalTo(0))
        assertThat(dbDef1.dao.findLogEntries("Label", "DELETE").size, equalTo(1))
        // Cascade delete has caused also this row to appear
        assertThat(dbDef1.dao.findLogEntries("BookmarkToLabel", "DELETE").size, equalTo(1))

        // Now these patch files are conflicting: in one, there's new usage of label1, in other, label1 is removed
        val patchFile1b = DatabaseSync.createPatchForDatabase(dbDef1)!!
        val patchFile2 = DatabaseSync.createPatchForDatabase(dbDef2)!!

        DatabaseSync.applyPatchesForDatabase(dbDef2, patchFile1b)
        val bls2 = dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id)
        assertThat(bls2.size, equalTo(0))

        // We try to add BookmarkToLabel to a label that does not exist any more.
        DatabaseSync.applyPatchesForDatabase(dbDef1, patchFile2)
        val bls1 = dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id)
        assertThat(bls1.size, equalTo(0))
        checkLog(dbDef1, dbDef2)
    }

    @Test
    fun testBookmarkToLabelIsAddedAgain() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.Bookmark()
        val bookmark2 = BookmarkEntities.Bookmark()
        val bl1 = BookmarkEntities.BookmarkToLabel(bookmark1, label1)
        dbDef1.localDb.bookmarkDao().insert(bookmark1)
        dbDef2.localDb.bookmarkDao().insert(bookmark2)
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(bl1)
        sync(dbDef1, dbDef2)
        assertThat(dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))

        dbDef2.localDb.bookmarkDao().delete(bl1)
        sync(dbDef1, dbDef2)
        assertThat(dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(0))
        assertThat(dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(0))

        dbDef1.localDb.bookmarkDao().insert(bl1)
        sync(dbDef1, dbDef2)
        assertThat(dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))
    }

    @Test
    fun testBookmarkToLabelIsAddedAgain1() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.Bookmark()
        val bookmark2 = BookmarkEntities.Bookmark()
        val bl1 = BookmarkEntities.BookmarkToLabel(bookmark1, label1)
        dbDef1.localDb.bookmarkDao().insert(bookmark1)
        dbDef2.localDb.bookmarkDao().insert(bookmark2)
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(bl1)
        sync(dbDef1, dbDef2)
        assertThat(dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))

        dbDef2.localDb.bookmarkDao().delete(bl1)
        dbDef1.localDb.bookmarkDao().delete(bl1)
        // but here it is inserted back!
        dbDef1.localDb.bookmarkDao().insert(bl1)

        sync(dbDef1, dbDef2)
        assertThat(dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id).size, equalTo(1))
    }

    @Test
    fun testThreeDevices() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef3 = getDbDef(File.createTempFile("bookmarks3-", ".sqlite3", CommonUtils.tmpDir))
        val bookmark1 = BookmarkEntities.Bookmark()
        val bookmark2 = BookmarkEntities.Bookmark()
        val bookmark3 = BookmarkEntities.Bookmark()
        dbDef1.localDb.bookmarkDao().insert(bookmark1)
        dbDef2.localDb.bookmarkDao().insert(bookmark2)
        dbDef3.localDb.bookmarkDao().insert(bookmark3)
        sync3(dbDef1, dbDef2, dbDef3)
        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(3))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(3))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(3))
        dbDef1.localDb.bookmarkDao().delete(bookmark3)
        dbDef2.localDb.bookmarkDao().delete(bookmark1)
        dbDef3.localDb.bookmarkDao().delete(bookmark2)
        sync3(dbDef1, dbDef2, dbDef3)
        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(0))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(0))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(0))
    }

    @Test
    fun testThreeDevices1() {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef3 = getDbDef(File.createTempFile("bookmarks3-", ".sqlite3", CommonUtils.tmpDir))
        val bookmark1 = BookmarkEntities.Bookmark()
        val bookmark2 = BookmarkEntities.Bookmark()
        val bookmark3 = BookmarkEntities.Bookmark()
        dbDef1.localDb.bookmarkDao().insert(bookmark1)
        sync3(dbDef1, dbDef2, dbDef3)
        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(1))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(1))

        dbDef2.localDb.bookmarkDao().insert(bookmark2)
        sync3(dbDef1, dbDef2, dbDef3)

        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(2))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(2))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(2))

        dbDef3.localDb.bookmarkDao().insert(bookmark3)
        sync3(dbDef1, dbDef2, dbDef3)

        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(3))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(3))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(3))
        dbDef1.localDb.bookmarkDao().delete(bookmark3)
        dbDef2.localDb.bookmarkDao().delete(bookmark1)
        dbDef3.localDb.bookmarkDao().delete(bookmark2)
        sync3(dbDef1, dbDef2, dbDef3)
        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(0))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(0))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(0))
    }
}
