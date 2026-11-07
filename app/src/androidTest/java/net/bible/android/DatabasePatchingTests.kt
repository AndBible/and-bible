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
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.getColumnNames
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.common.CommonUtils
import net.bible.service.cloudsync.SyncableDatabaseDefinition

import net.bible.service.db.DatabaseContainer
import net.bible.service.cloudsync.*
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.platform.AppEnvironment
import java.io.File
import java.util.UUID
import org.crosswire.jsword.versification.BibleBook

infix fun <E, T : Iterable<E>> T?.mustEqualTo(theOther: Iterable<E>) = Assert.assertEquals(theOther, this)

@RunWith(AndroidJUnit4::class)
@SmallTest
class DatabasePatchingTests {
    private val env: AppEnvironment by lazy { org.koin.java.KoinJavaComponent.get(AppEnvironment::class.java) }

    @Before
    fun setUp() {
        DatabaseContainer.ready = true
        DatabaseContainer.instance
    }

    private suspend fun getDbDef(dbFile1: File): SyncableDatabaseAccessor<BookmarkDatabase> {
        var bmarkDb = DatabaseContainer.instance.getBookmarkDb(dbFile1.absolutePath)
        val dbDef = SyncableDatabaseAccessor(
            bmarkDb,
            {DatabaseContainer.instance.getBookmarkDb(it)},
            {
                bmarkDb.close()
                bmarkDb = DatabaseContainer.instance.getBookmarkDb(dbFile1.absolutePath)
                bmarkDb
            },
            dbFile1.path,
            SyncableDatabaseDefinition.BOOKMARKS,
            deviceId = UUID.randomUUID().toString(),
        )
        createTriggers(dbDef)
        return dbDef
    }

    /** A fresh database of [category] standing in for one device. */
    private suspend fun <T : SyncableRoomDatabase> newDevice(
        category: SyncableDatabaseDefinition,
        open: (String) -> T,
    ): SyncableDatabaseAccessor<T> {
        val dbFile = File.createTempFile("${category.name.lowercase()}-", ".sqlite3", CommonUtils.tmpDir)
        var db = open(dbFile.absolutePath)
        val dbDef = SyncableDatabaseAccessor(
            db,
            open,
            {
                db.close()
                db = open(dbFile.absolutePath)
                db
            },
            dbFile.path,
            category,
            deviceId = UUID.randomUUID().toString(),
        )
        createTriggers(dbDef)
        return dbDef
    }

    private suspend fun workspaceDevice() = newDevice(SyncableDatabaseDefinition.WORKSPACES) { DatabaseContainer.instance.getWorkspaceDb(it) }
    private suspend fun bookmarkDevice() = newDevice(SyncableDatabaseDefinition.BOOKMARKS) { DatabaseContainer.instance.getBookmarkDb(it) }
    private suspend fun myDocumentDevice() = newDevice(SyncableDatabaseDefinition.MYDOCUMENTS) { DatabaseContainer.instance.getMyDocumentDb(it) }
    private suspend fun aiSettingsDevice() = newDevice(SyncableDatabaseDefinition.AI_SETTINGS) { DatabaseContainer.instance.getAiSettingsDb(it) }

    private suspend fun sync(dbDef1: SyncableDatabaseAccessor<*>, dbDef2: SyncableDatabaseAccessor<*>) {
        val patch1 = createPatchForDatabase(dbDef1, env)
        val patch2 = createPatchForDatabase(dbDef2, env)
        applyPatchesForDatabase(dbDef1, env, patch2)
        applyPatchesForDatabase(dbDef2, env, patch1)
        checkLog(dbDef1, dbDef2)
    }

    private suspend fun sync3(dbDef1: SyncableDatabaseAccessor<*>, dbDef2: SyncableDatabaseAccessor<*>, dbDef3: SyncableDatabaseAccessor<*>) {
        val patch1 = createPatchForDatabase(dbDef1, env)
        val patch2 = createPatchForDatabase(dbDef2, env)
        val patch3 = createPatchForDatabase(dbDef3, env)
        applyPatchesForDatabase(dbDef1, env, patch2, patch3)
        applyPatchesForDatabase(dbDef2, env, patch1, patch3)
        applyPatchesForDatabase(dbDef3, env, patch1, patch2)
        checkLog(dbDef1, dbDef2)
        checkLog(dbDef2, dbDef3)
    }

    private suspend fun checkLog(dbDef1: SyncableDatabaseAccessor<*>, dbDef2: SyncableDatabaseAccessor<*>) {
        dbDef1.dao.allLogEntries() mustEqualTo dbDef2.dao.allLogEntries()
    }

    @Test
    fun testSimplePatchFileWritingAndReading() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        assertThat(dbDef1.localDb.syncDao().allLogEntries().size, equalTo(2))

        val patchFile = createPatchForDatabase(dbDef1, env)!!
        applyPatchesForDatabase(dbDef2, env, patchFile)
        checkLog(dbDef1, dbDef2)
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(createPatchForDatabase(dbDef1, env), equalTo(null))
        assertThat(createPatchForDatabase(dbDef2, env), equalTo(null))
    }

    @Test
    fun testMergingChanges() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 3"))
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 4"))

        sync(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(4))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(4))

        assertThat(createPatchForDatabase(dbDef1, env), equalTo(null))
        assertThat(createPatchForDatabase(dbDef2, env), equalTo(null))
    }

    @Test
    fun testBasicDeletion() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = createPatchForDatabase(dbDef1, env)!!
        applyPatchesForDatabase(dbDef2, env, patchFile1)
        checkLog(dbDef1, dbDef2)

        dbDef2.localDb.bookmarkDao().delete(label1)
        val patchFile2 = createPatchForDatabase(dbDef2, env)!!
        applyPatchesForDatabase(dbDef1, env, patchFile2)
        checkLog(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(1))

        assertThat(createPatchForDatabase(dbDef1, env), equalTo(null))
        assertThat(createPatchForDatabase(dbDef2, env), equalTo(null))
    }

    @Test
    fun testBasicUpdate() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = createPatchForDatabase(dbDef1, env)!!
        applyPatchesForDatabase(dbDef2, env, patchFile1)
        checkLog(dbDef1, dbDef2)

        val label1mod = label1.copy()
        label1mod.name = "label 1 mod"
        dbDef2.localDb.bookmarkDao().update(label1mod)
        val patchFile2 = createPatchForDatabase(dbDef2, env)!!
        applyPatchesForDatabase(dbDef1, env, patchFile2)
        checkLog(dbDef1, dbDef2)

        assertThat(dbDef1.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))
        assertThat(dbDef2.localDb.bookmarkDao().allLabelsSortedByName().size, equalTo(2))

        assertThat(dbDef1.localDb.bookmarkDao().labelById(label1.id)?.name, equalTo("label 1 mod"));

        assertThat(createPatchForDatabase(dbDef1, env), equalTo(null))
        assertThat(createPatchForDatabase(dbDef2, env), equalTo(null))
    }

    @Test
    fun testSimultaneousUpdate() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))

        val label1 = BookmarkEntities.Label(name = "label 1")
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))

        val patchFile1 = createPatchForDatabase(dbDef1, env)!!
        applyPatchesForDatabase(dbDef2, env, patchFile1)
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

        assertThat(createPatchForDatabase(dbDef1, env), equalTo(null))
        assertThat(createPatchForDatabase(dbDef2, env), equalTo(null))
    }

    @Test
    fun testBookmarkToLabelUpdates() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark2 = BookmarkEntities.BibleBookmarkWithNotes()
        val bl1 = BookmarkEntities.BibleBookmarkToLabel(bookmark1.bookmarkEntity, label1)
        dbDef1.localDb.bookmarkDao().insert(bookmark1.bookmarkEntity)
        dbDef2.localDb.bookmarkDao().insert(bookmark2.bookmarkEntity)
        dbDef1.localDb.bookmarkDao().insert(label1)
        dbDef1.localDb.bookmarkDao().insert(bl1)

        val patchFile1 = createPatchForDatabase(dbDef1, env)!!
        applyPatchesForDatabase(dbDef2, env, patchFile1)
        val bl2 = BookmarkEntities.BibleBookmarkToLabel(bookmark2.bookmarkEntity, label1)
        dbDef2.localDb.bookmarkDao().insert(bl2)
        dbDef1.localDb.bookmarkDao().delete(label1)

        assertThat(dbDef1.dao.findLogEntries("BibleBookmark", "UPSERT").size, equalTo(1))
        assertThat(dbDef1.dao.findLogEntries("Label", "UPSERT").size, equalTo(0))
        assertThat(dbDef1.dao.findLogEntries("Label", "DELETE").size, equalTo(1))
        // The cascaded link delete is not logged: the label's delete covers it
        assertThat(dbDef1.dao.findLogEntries("BibleBookmarkToLabel", "DELETE").size, equalTo(0))

        // Now these patch files are conflicting: in one, there's new usage of label1, in other, label1 is removed
        val patchFile1b = createPatchForDatabase(dbDef1, env)!!
        val patchFile2 = createPatchForDatabase(dbDef2, env)!!

        applyPatchesForDatabase(dbDef2, env, patchFile1b)
        val bls2 = dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id)
        assertThat(bls2.size, equalTo(0))

        // We try to add BookmarkToLabel to a label that does not exist any more.
        applyPatchesForDatabase(dbDef1, env, patchFile2)
        val bls1 = dbDef1.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark1.id)
        assertThat(bls1.size, equalTo(0))
        checkLog(dbDef1, dbDef2)
    }

    @Test
    fun testBookmarkToLabelIsAddedAgain() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark2 = BookmarkEntities.BibleBookmarkWithNotes()
        val bl1 = BookmarkEntities.BibleBookmarkToLabel(bookmark1.bookmarkEntity, label1)
        dbDef1.localDb.bookmarkDao().insert(bookmark1.bookmarkEntity)
        dbDef2.localDb.bookmarkDao().insert(bookmark2.bookmarkEntity)
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
    fun testBookmarkToLabelIsAddedAgain1() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark2 = BookmarkEntities.BibleBookmarkWithNotes()
        val bl1 = BookmarkEntities.BibleBookmarkToLabel(bookmark1.bookmarkEntity, label1)
        dbDef1.localDb.bookmarkDao().insert(bookmark1.bookmarkEntity)
        dbDef2.localDb.bookmarkDao().insert(bookmark2.bookmarkEntity)
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
    fun testThreeDevices() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef3 = getDbDef(File.createTempFile("bookmarks3-", ".sqlite3", CommonUtils.tmpDir))
        val bookmark1 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark2 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark3 = BookmarkEntities.BibleBookmarkWithNotes()
        dbDef1.localDb.bookmarkDao().insert(bookmark1.bookmarkEntity)
        dbDef2.localDb.bookmarkDao().insert(bookmark2.bookmarkEntity)
        dbDef3.localDb.bookmarkDao().insert(bookmark3.bookmarkEntity)
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
    fun testThreeDevices1() = runBlocking<Unit> {
        val dbDef1 = getDbDef(File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef2 = getDbDef(File.createTempFile("bookmarks2-", ".sqlite3", CommonUtils.tmpDir))
        val dbDef3 = getDbDef(File.createTempFile("bookmarks3-", ".sqlite3", CommonUtils.tmpDir))
        val bookmark1 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark2 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark3 = BookmarkEntities.BibleBookmarkWithNotes()
        dbDef1.localDb.bookmarkDao().insert(bookmark1.bookmarkEntity)
        sync3(dbDef1, dbDef2, dbDef3)
        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(1))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(1))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(1))

        dbDef2.localDb.bookmarkDao().insert(bookmark2.bookmarkEntity)
        sync3(dbDef1, dbDef2, dbDef3)

        assertThat(dbDef1.localDb.bookmarkDao().allBookmarks().size, equalTo(2))
        assertThat(dbDef2.localDb.bookmarkDao().allBookmarks().size, equalTo(2))
        assertThat(dbDef3.localDb.bookmarkDao().allBookmarks().size, equalTo(2))

        dbDef3.localDb.bookmarkDao().insert(bookmark3.bookmarkEntity)
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

    private fun newWindow(workspaceId: IdType) = WorkspaceEntities.Window(
        workspaceId = workspaceId,
        isSynchronized = true,
        isPinMode = false,
        windowLayout = WorkspaceEntities.WindowLayout("VISIBLE"),
    )

    private fun newPageManager(windowId: IdType) = WorkspaceEntities.PageManager(
        windowId = windowId,
        biblePage = WorkspaceEntities.BiblePage("KJV", WorkspaceEntities.Verse("KJV", BibleBook.PET1.ordinal, 4, 2)),
        commentaryPage = null,
        dictionaryPage = null,
        generalBookPage = null,
        mapPage = null,
        currentCategoryName = "BIBLE",
        textDisplaySettings = null,
        jsState = null,
    )

    private suspend fun insertWindowWithPage(dbDef: SyncableDatabaseAccessor<WorkspaceDatabase>): Pair<WorkspaceEntities.Window, WorkspaceEntities.PageManager> {
        val dao = dbDef.localDb.workspaceDao()
        val workspace = WorkspaceEntities.Workspace("workspace")
        val window = newWindow(workspace.id)
        val pageManager = newPageManager(window.id)
        dao.insertWorkspace(workspace)
        dao.insertWindow(window)
        dao.insertPageManager(pageManager)
        return Pair(window, pageManager)
    }

    private suspend fun assertEveryWindowHasPageManager(dbDef: SyncableDatabaseAccessor<WorkspaceDatabase>) {
        val dao = dbDef.localDb.workspaceDao()
        for (window in dao.allWindows()) {
            assertNotNull("Window ${window.id} has no PageManager on device ${dbDef.deviceId}", dao.pageManager(window.id))
        }
    }

    private suspend fun assertPagesMatch(
        dbDef1: SyncableDatabaseAccessor<WorkspaceDatabase>,
        dbDef2: SyncableDatabaseAccessor<WorkspaceDatabase>,
        windowId: IdType,
        biblePage: WorkspaceEntities.BiblePage,
    ) {
        assertEveryWindowHasPageManager(dbDef1)
        assertEveryWindowHasPageManager(dbDef2)
        assertThat(dbDef1.localDb.workspaceDao().pageManager(windowId)?.biblePage, equalTo(biblePage))
        assertThat(dbDef2.localDb.workspaceDao().pageManager(windowId)?.biblePage, equalTo(biblePage))
    }

    /**
     * A window-only change must not count as a page edit. An earlier unsynced verse change
     * on the other device still wins, and the window change still syncs.
     */
    @Test
    fun testWindowMoveDoesNotOverwriteUnsyncedVerseChange() = runBlocking<Unit> {
        val dbDef1 = workspaceDevice()
        val dbDef2 = workspaceDevice()
        val dao1 = dbDef1.localDb.workspaceDao()
        val dao2 = dbDef2.localDb.workspaceDao()

        val (window, pageManager) = insertWindowWithPage(dbDef1)
        sync(dbDef1, dbDef2)

        val navigated = pageManager.copy(
            biblePage = WorkspaceEntities.BiblePage("KJV", WorkspaceEntities.Verse("KJV", BibleBook.JOHN.ordinal, 1, 18))
        )
        Thread.sleep(10)
        dao1.updatePageManagers(listOf(navigated))
        Thread.sleep(10)
        dao2.updateWindows(listOf(window.deepCopy().apply { orderNumber = 1 }))
        Thread.sleep(10)

        sync(dbDef1, dbDef2)
        assertPagesMatch(dbDef1, dbDef2, window.id, navigated.biblePage)
        assertThat(dao1.allWindows().single().orderNumber, equalTo(1))
        assertThat(dao2.allWindows().single().orderNumber, equalTo(1))

        sync(dbDef1, dbDef2)
        assertPagesMatch(dbDef1, dbDef2, window.id, navigated.biblePage)
    }

    /**
     * One parent row and the rows its delete cascades to, in [childTables]. [create] inserts
     * them all on one device; [deleteParent] and [editParent] change only the parent row.
     */
    private class Family<T : SyncableRoomDatabase>(
        val newDevice: suspend () -> SyncableDatabaseAccessor<T>,
        val childTables: List<String>,
        val create: suspend T.() -> Unit,
        val deleteParent: suspend T.() -> Unit,
        val editParent: suspend T.() -> Unit,
    )

    /** Every row of each table, as text, sorted. */
    private suspend fun rows(dbDef: SyncableDatabaseAccessor<*>, tables: List<String>): Map<String, List<String>> =
        dbDef.localDb.useWriterConnectionMarked { c ->
            tables.associateWith { table ->
                val names = c.queryRows("SELECT name FROM pragma_table_info('$table')") { it.getText(0) }
                val columns = names.joinToString(" || '|' || ") { "quote(`$it`)" }
                c.queryRows("SELECT $columns FROM `$table`") { it.getText(0) }.sorted()
            }
        }

    /**
     * Swaps patches between all devices, then checks that their logs match. The last device
     * applies the others' patches in reverse order.
     */
    private suspend fun syncAll(devices: List<SyncableDatabaseAccessor<*>>) {
        val patches = devices.map { createPatchForDatabase(it, env) }
        devices.forEachIndexed { i, device ->
            val others = patches.filterIndexed { j, _ -> j != i }
            val ordered = if (i == devices.lastIndex) others.reversed() else others
            applyPatchesForDatabase(device, env, *ordered.toTypedArray())
        }
        devices.zipWithNext { a, b -> checkLog(a, b) }
    }

    /** Four devices sharing [family]'s rows. Without log entries, the rows predate sync. */
    private suspend fun <T : SyncableRoomDatabase> devicesWith(family: Family<T>, withLogEntries: Boolean = true): List<SyncableDatabaseAccessor<T>> {
        val devices = List(4) { family.newDevice() }
        family.create(devices[0].localDb)
        syncAll(devices)
        if (!withLogEntries) {
            // As after enabling sync, which clears the log on the first device and copies its database
            devices.forEach { it.dao.clearLog() }
        }
        val children = rows(devices[0], family.childTables)
        for (table in family.childTables) {
            assertTrue("$table has no rows", children[table]!!.isNotEmpty())
        }
        return devices
    }

    /**
     * Device 1 deletes the parent, then device 2, not yet synced, edits only the parent row.
     * The newer edit keeps the parent on every device, so the rows the delete cascaded to must
     * come back too, whatever order the patches arrive in.
     */
    private suspend fun <T : SyncableRoomDatabase> assertNewerEditKeepsChildren(family: Family<T>, withLogEntries: Boolean) {
        val devices = devicesWith(family, withLogEntries)
        val children = rows(devices[0], family.childTables)

        // Log timestamps are milliseconds; keep the steps apart.
        Thread.sleep(10)
        family.deleteParent(devices[0].localDb)
        Thread.sleep(10)
        family.editParent(devices[1].localDb)
        Thread.sleep(10)

        syncAll(devices)
        for (device in devices) {
            assertThat(rows(device, family.childTables), equalTo(children))
        }
        // A later sync must not delete them again.
        syncAll(devices)
        for (device in devices) {
            assertThat(rows(device, family.childTables), equalTo(children))
        }
    }

    /** A delete newer than the other device's edit still removes the parent's children everywhere. */
    private suspend fun <T : SyncableRoomDatabase> assertNewerDeleteRemovesChildren(family: Family<T>) {
        val devices = devicesWith(family)

        Thread.sleep(10)
        family.editParent(devices[1].localDb)
        Thread.sleep(10)
        family.deleteParent(devices[0].localDb)
        Thread.sleep(10)

        syncAll(devices)
        val none = family.childTables.associateWith { emptyList<String>() }
        for (device in devices) {
            assertThat(rows(device, family.childTables), equalTo(none))
        }
    }

    private suspend fun <T : SyncableRoomDatabase> testParentDeleteConflicts(family: () -> Family<T>) {
        assertNewerEditKeepsChildren(family(), withLogEntries = true)
        assertNewerEditKeepsChildren(family(), withLogEntries = false)
        assertNewerDeleteRemovesChildren(family())
    }

    private fun windowFamily(): Family<WorkspaceDatabase> {
        val workspace = WorkspaceEntities.Workspace("workspace")
        val window = newWindow(workspace.id)
        return Family(::workspaceDevice, listOf("PageManager"),
            create = { workspaceDao().run {
                insertWorkspace(workspace)
                insertWindow(window)
                insertPageManager(newPageManager(window.id))
            } },
            deleteParent = { workspaceDao().deleteWindow(window.id) },
            editParent = { workspaceDao().updateWindows(listOf(window.deepCopy().apply { orderNumber = 1 })) },
        )
    }

    private fun workspaceFamily(): Family<WorkspaceDatabase> {
        val workspace = WorkspaceEntities.Workspace("workspace")
        val window = newWindow(workspace.id)
        return Family(::workspaceDevice, listOf("Window", "PageManager", "WorkspaceLabelOverride"),
            create = { workspaceDao().run {
                insertWorkspace(workspace)
                insertWindow(window)
                insertPageManager(newPageManager(window.id))
                insertOrUpdateLabelOverride(WorkspaceEntities.WorkspaceLabelOverride(workspace.id, IdType(), 0))
            } },
            deleteParent = { workspaceDao().deleteWorkspace(workspace.id) },
            editParent = { workspaceDao().updateWorkspace(workspace.copy(name = "renamed")) },
        )
    }

    private fun bibleBookmarkFamily(): Family<BookmarkDatabase> {
        val label = BookmarkEntities.Label(name = "label")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes().bookmarkEntity
        return Family(::bookmarkDevice, listOf("BibleBookmarkNotes", "BibleBookmarkToLabel"),
            create = { bookmarkDao().run {
                insert(label)
                insert(bookmark)
                insert(BookmarkEntities.BibleBookmarkNotes(bookmark.id, "note"))
                insert(BookmarkEntities.BibleBookmarkToLabel(bookmark, label))
            } },
            deleteParent = { bookmarkDao().deleteBookmarksById(listOf(bookmark.id)) },
            editParent = { bookmarkDao().update(bookmark.copy(wholeVerse = true)) },
        )
    }

    private fun genericBookmarkFamily(): Family<BookmarkDatabase> {
        val label = BookmarkEntities.Label(name = "label")
        val bookmark = BookmarkEntities.GenericBookmark(
            key = "key", bookInitials = "Book", ordinalStart = null, ordinalEnd = null,
            startOffset = null, endOffset = null, customIcon = null,
        )
        return Family(::bookmarkDevice, listOf("GenericBookmarkNotes", "GenericBookmarkToLabel"),
            create = { bookmarkDao().run {
                insert(label)
                insert(bookmark)
                insert(BookmarkEntities.GenericBookmarkNotes(bookmark.id, "note"))
                insertGenericBookmarkToLabels(listOf(BookmarkEntities.GenericBookmarkToLabel(bookmark, label)))
            } },
            deleteParent = { bookmarkDao().deleteGenericBookmarksById(listOf(bookmark.id)) },
            editParent = { bookmarkDao().update(bookmark.copy(wholeVerse = true)) },
        )
    }

    private fun labelFamily(): Family<BookmarkDatabase> {
        val label = BookmarkEntities.Label(name = "study pad")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes().bookmarkEntity
        val entry = BookmarkEntities.StudyPadTextEntry(labelId = label.id, orderNumber = 0)
        return Family(::bookmarkDevice, listOf("BibleBookmarkToLabel", "StudyPadTextEntry", "StudyPadTextEntryText"),
            create = { bookmarkDao().run {
                insert(label)
                insert(bookmark)
                insert(BookmarkEntities.BibleBookmarkToLabel(bookmark, label))
                insert(entry)
                insert(BookmarkEntities.StudyPadTextEntryText(entry.id, "text"))
            } },
            deleteParent = { bookmarkDao().delete(label) },
            editParent = { bookmarkDao().update(label.copy(name = "renamed")) },
        )
    }

    private fun myDocumentFamily(): Family<MyDocumentDatabase> {
        val document = MyDocument(name = "document", initials = "MyDoc_test")
        val page = MyDocumentPage(documentId = document.id, title = "page", pageKey = "page", orderNumber = 0)
        return Family(::myDocumentDevice, listOf("MyDocumentPage", "MyDocumentPageContent"),
            create = { myDocumentDao().run {
                insert(document)
                insertPageWithContent(page, "content")
            } },
            deleteParent = { myDocumentDao().delete(document) },
            editParent = { myDocumentDao().update(document.copy(name = "renamed")) },
        )
    }

    private fun llmProviderFamily(): Family<AiSettingsDatabase> {
        val provider = LlmProviderConfig(providerType = "OPENAI", displayName = "provider")
        return Family(::aiSettingsDevice, listOf("LlmConfiguredModel"),
            create = {
                llmProviderConfigDao().insert(provider)
                llmConfiguredModelDao().insert(LlmConfiguredModel(providerConfigId = provider.id, modelId = "model"))
            },
            deleteParent = { llmProviderConfigDao().delete(provider) },
            editParent = { llmProviderConfigDao().update(provider.copy(displayName = "renamed")) },
        )
    }

    /**
     * Device 1 closes a window while device 2 changes only that window's row (order,
     * minimise, pin...). The window survives, so its page must too: without it the window
     * opens at Genesis 1:1 and never saves its position again.
     */
    @Test
    fun testParentDeleteConflictsWindow() = runBlocking<Unit> { testParentDeleteConflicts(::windowFamily) }

    @Test
    fun testParentDeleteConflictsWorkspace() = runBlocking<Unit> { testParentDeleteConflicts(::workspaceFamily) }

    @Test
    fun testParentDeleteConflictsBibleBookmark() = runBlocking<Unit> { testParentDeleteConflicts(::bibleBookmarkFamily) }

    @Test
    fun testParentDeleteConflictsGenericBookmark() = runBlocking<Unit> { testParentDeleteConflicts(::genericBookmarkFamily) }

    @Test
    fun testParentDeleteConflictsLabel() = runBlocking<Unit> { testParentDeleteConflicts(::labelFamily) }

    @Test
    fun testParentDeleteConflictsMyDocument() = runBlocking<Unit> { testParentDeleteConflicts(::myDocumentFamily) }

    @Test
    fun testParentDeleteConflictsLlmProvider() = runBlocking<Unit> { testParentDeleteConflicts(::llmProviderFamily) }

    /**
     * A note cleared before its bookmark was deleted was a delete of its own. When a newer
     * bookmark edit elsewhere brings the bookmark back, the note must stay cleared.
     */
    @Test
    fun testClearedNoteStaysClearedWhenBookmarkComesBack() = runBlocking<Unit> {
        val family = bibleBookmarkFamily()
        val devices = devicesWith(family)
        val bookmarkId = devices[0].localDb.bookmarkDao().allBookmarks().single().id
        val links = rows(devices[0], listOf("BibleBookmarkToLabel"))

        Thread.sleep(10)
        devices[0].localDb.bookmarkDao().deleteBookmarkNotes(bookmarkId)
        Thread.sleep(10)
        family.deleteParent(devices[0].localDb)
        Thread.sleep(10)
        family.editParent(devices[1].localDb)
        Thread.sleep(10)

        syncAll(devices)
        for (device in devices) {
            assertThat(device.localDb.bookmarkDao().allBookmarks().single().id, equalTo(bookmarkId))
            assertThat(rows(device, listOf("BibleBookmarkNotes"))["BibleBookmarkNotes"], equalTo(emptyList<String>()))
            assertThat(rows(device, listOf("BibleBookmarkToLabel")), equalTo(links))
        }
    }

    /**
     * Every foreign key that cascades deletes between synced tables must be declared in
     * [SyncableDatabaseDefinition.Table.parents], after its parent table. Otherwise a new
     * table silently brings back the lost-children bug.
     */
    @Test
    fun testEveryCascadeIsDeclaredAsParent() = runBlocking<Unit> {
        val notCarried = setOf("AiPageCacheEntry")
        val devices = listOf(
            bookmarkDevice(),
            workspaceDevice(),
            newDevice(SyncableDatabaseDefinition.READINGPLANS) { DatabaseContainer.instance.getReadingPlanDb(it) },
            myDocumentDevice(),
            aiSettingsDevice(),
            newDevice(SyncableDatabaseDefinition.PROGRESS) { DatabaseContainer.instance.getProgressDb(it) },
        )
        assertThat(devices.map { it.category }, equalTo(SyncableDatabaseDefinition.ALL.toList()))
        for (dbDef in devices) {
            val tables = dbDef.tableDefinitions
            tables.forEachIndexed { i, table ->
                val cascades = mutableMapOf<String, String>()
                dbDef.localDb.useWriterConnectionMarked { c ->
                    c.queryRows("SELECT `table`, `from` FROM pragma_foreign_key_list('${table.tableName}') WHERE on_delete = 'CASCADE'") {
                        it.getText(0) to it.getText(1)
                    }
                }.forEach { (parent, column) -> cascades[parent] = column }
                val expected = if (table.tableName in notCarried) emptyMap() else cascades
                assertEquals(table.tableName, expected, table.parents)
                for (parent in table.parents.keys) {
                    assertTrue("$parent must come before ${table.tableName}", tables.take(i).any { it.tableName == parent })
                }
            }
        }
    }
}
