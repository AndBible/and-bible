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

package net.bible.service.cloudsync

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.aiSettingsMigrations
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.database.migrations.myDocumentMigrations
import net.bible.android.database.migrations.progressMigrations
import net.bible.android.database.migrations.readingPlanMigrations
import net.bible.android.database.migrations.workspacesMigrations
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.common.CommonUtils
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import okio.Path
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Parent-row deletes that lose a sync conflict to a newer edit on another device (PR #3889, support ticket 3388):
 * the rows the delete cascaded to (a window's PageManager, a bookmark's notes and label links, ...) must come back
 * with the parent, and when the delete wins, the children go everywhere. Ported from the instrumented
 * `DatabasePatchingTests` to the suspend sync API. Several devices swap patches in different orders, with and
 * without log entries for the rows (rows that predate sync).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ParentDeleteConflictSyncTest {
    private val opened = mutableListOf<SyncableDatabaseAccessor<*>>()
    private val files = mutableListOf<File>()
    private val env get() = KoinJavaComponent.get<AppEnvironment>(AppEnvironment::class.java)

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        opened.forEach { it.localDb.close() }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        DatabaseResetter.resetDatabase()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(120_000) { block() } }

    private fun sleep() = Thread.sleep(10)

    /** A fresh database of [category] standing in for one device. */
    private suspend fun <T : SyncableRoomDatabase> newDevice(
        category: SyncableDatabaseDefinition,
        open: (String) -> T,
    ): SyncableDatabaseAccessor<T> {
        val file = File.createTempFile("${category.name.lowercase()}-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, open, { db.close(); db = open(file.absolutePath); db },
            file.path, category, deviceId = UUID.randomUUID().toString())
        dropTriggers(dbDef)
        createTriggers(dbDef)
        return dbDef.also { opened += it }
    }

    private fun <T : RoomDatabase> build(klass: Class<T>, path: String, migrations: Array<out androidx.room3.migration.Migration>): T =
        Room.databaseBuilder(application, klass, path)
            .addMigrations(*migrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()

    private suspend fun workspaceDevice() = newDevice(SyncableDatabaseDefinition.WORKSPACES) {
        build(WorkspaceDatabase::class.java, it, workspacesMigrations)
    }
    private suspend fun bookmarkDevice() = newDevice(SyncableDatabaseDefinition.BOOKMARKS) {
        build(BookmarkDatabase::class.java, it, bookmarkMigrations)
    }
    private suspend fun myDocumentDevice() = newDevice(SyncableDatabaseDefinition.MYDOCUMENTS) {
        build(MyDocumentDatabase::class.java, it, myDocumentMigrations)
    }
    private suspend fun aiSettingsDevice() = newDevice(SyncableDatabaseDefinition.AI_SETTINGS) {
        build(AiSettingsDatabase::class.java, it, aiSettingsMigrations)
    }

    private suspend fun sync(dbDef1: SyncableDatabaseAccessor<*>, dbDef2: SyncableDatabaseAccessor<*>) {
        val patch1: Path? = createPatchForDatabase(dbDef1, env)
        val patch2: Path? = createPatchForDatabase(dbDef2, env)
        applyPatchesForDatabase(dbDef1, env, patch2)
        applyPatchesForDatabase(dbDef2, env, patch1)
        checkLog(dbDef1, dbDef2)
    }

    private suspend fun checkLog(dbDef1: SyncableDatabaseAccessor<*>, dbDef2: SyncableDatabaseAccessor<*>) {
        assertEquals(dbDef2.dao.allLogEntries(), dbDef1.dao.allLogEntries())
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
        assertEquals(biblePage, dbDef1.localDb.workspaceDao().pageManager(windowId)?.biblePage)
        assertEquals(biblePage, dbDef2.localDb.workspaceDao().pageManager(windowId)?.biblePage)
    }

    /**
     * A window-only change must not count as a page edit. An earlier unsynced verse change
     * on the other device still wins, and the window change still syncs.
     */
    @Test
    fun testWindowMoveDoesNotOverwriteUnsyncedVerseChange() = run {
        val dbDef1 = workspaceDevice()
        val dbDef2 = workspaceDevice()
        val dao1 = dbDef1.localDb.workspaceDao()
        val dao2 = dbDef2.localDb.workspaceDao()

        val (window, pageManager) = insertWindowWithPage(dbDef1)
        sync(dbDef1, dbDef2)

        val navigated = pageManager.copy(
            biblePage = WorkspaceEntities.BiblePage("KJV", WorkspaceEntities.Verse("KJV", BibleBook.JOHN.ordinal, 1, 18))
        )
        sleep()
        dao1.updatePageManagers(listOf(navigated))
        sleep()
        dao2.updateWindows(listOf(window.deepCopy().apply { orderNumber = 1 }))
        sleep()

        sync(dbDef1, dbDef2)
        assertPagesMatch(dbDef1, dbDef2, window.id, navigated.biblePage)
        assertEquals(1, dao1.allWindows().single().orderNumber)
        assertEquals(1, dao2.allWindows().single().orderNumber)

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
        for (i in 0 until devices.lastIndex) checkLog(devices[i], devices[i + 1])
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
        sleep()
        family.deleteParent(devices[0].localDb)
        sleep()
        family.editParent(devices[1].localDb)
        sleep()

        syncAll(devices)
        for (device in devices) {
            assertEquals(children, rows(device, family.childTables))
        }
        // A later sync must not delete them again.
        syncAll(devices)
        for (device in devices) {
            assertEquals(children, rows(device, family.childTables))
        }
    }

    /** A delete newer than the other device's edit still removes the parent's children everywhere. */
    private suspend fun <T : SyncableRoomDatabase> assertNewerDeleteRemovesChildren(family: Family<T>) {
        val devices = devicesWith(family)

        sleep()
        family.editParent(devices[1].localDb)
        sleep()
        family.deleteParent(devices[0].localDb)
        sleep()

        syncAll(devices)
        val none = family.childTables.associateWith { emptyList<String>() }
        for (device in devices) {
            assertEquals(none, rows(device, family.childTables))
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
        return Family({ workspaceDevice() }, listOf("PageManager"),
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
        return Family({ workspaceDevice() }, listOf("Window", "PageManager", "WorkspaceLabelOverride"),
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
        return Family({ bookmarkDevice() }, listOf("BibleBookmarkNotes", "BibleBookmarkToLabel"),
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
        return Family({ bookmarkDevice() }, listOf("GenericBookmarkNotes", "GenericBookmarkToLabel"),
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
        return Family({ bookmarkDevice() }, listOf("BibleBookmarkToLabel", "StudyPadTextEntry", "StudyPadTextEntryText"),
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
        return Family({ myDocumentDevice() }, listOf("MyDocumentPage", "MyDocumentPageContent"),
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
        return Family({ aiSettingsDevice() }, listOf("LlmConfiguredModel"),
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
    fun testParentDeleteConflictsWindow() = run { testParentDeleteConflicts(::windowFamily) }

    @Test
    fun testParentDeleteConflictsWorkspace() = run { testParentDeleteConflicts(::workspaceFamily) }

    @Test
    fun testParentDeleteConflictsBibleBookmark() = run { testParentDeleteConflicts(::bibleBookmarkFamily) }

    @Test
    fun testParentDeleteConflictsGenericBookmark() = run { testParentDeleteConflicts(::genericBookmarkFamily) }

    @Test
    fun testParentDeleteConflictsLabel() = run { testParentDeleteConflicts(::labelFamily) }

    @Test
    fun testParentDeleteConflictsMyDocument() = run { testParentDeleteConflicts(::myDocumentFamily) }

    @Test
    fun testParentDeleteConflictsLlmProvider() = run { testParentDeleteConflicts(::llmProviderFamily) }

    /**
     * A note cleared before its bookmark was deleted was a delete of its own. When a newer
     * bookmark edit elsewhere brings the bookmark back, the note must stay cleared.
     */
    @Test
    fun testClearedNoteStaysClearedWhenBookmarkComesBack() = run {
        val family = bibleBookmarkFamily()
        val devices = devicesWith(family)
        val bookmarkId = devices[0].localDb.bookmarkDao().allBookmarks().single().id
        val links = rows(devices[0], listOf("BibleBookmarkToLabel"))

        sleep()
        devices[0].localDb.bookmarkDao().deleteBookmarkNotes(bookmarkId)
        sleep()
        family.deleteParent(devices[0].localDb)
        sleep()
        family.editParent(devices[1].localDb)
        sleep()

        syncAll(devices)
        for (device in devices) {
            assertEquals(bookmarkId, device.localDb.bookmarkDao().allBookmarks().single().id)
            assertEquals(emptyList<String>(), rows(device, listOf("BibleBookmarkNotes"))["BibleBookmarkNotes"])
            assertEquals(links, rows(device, listOf("BibleBookmarkToLabel")))
        }
    }

    /**
     * Every foreign key that cascades deletes between synced tables must be declared in
     * [SyncableDatabaseDefinition.Table.parents], after its parent table. Otherwise a new
     * table silently brings back the lost-children bug.
     */
    @Test
    fun testEveryCascadeIsDeclaredAsParent() = run {
        val notCarried = setOf("AiPageCacheEntry")
        val devices = listOf(
            bookmarkDevice(),
            workspaceDevice(),
            newDevice(SyncableDatabaseDefinition.READINGPLANS) { build(ReadingPlanDatabase::class.java, it, readingPlanMigrations) },
            myDocumentDevice(),
            aiSettingsDevice(),
            newDevice(SyncableDatabaseDefinition.PROGRESS) { build(ProgressDatabase::class.java, it, progressMigrations) },
        )
        assertEquals(SyncableDatabaseDefinition.ALL.toList(), devices.map { it.category })
        for (dbDef in devices) {
            val tables = dbDef.tableDefinitions
            tables.forEachIndexed { i, table ->
                // PRAGMA foreign_key_list columns: id, seq, table, from, to, on_update, on_delete, match
                val cascades = dbDef.localDb.useWriterConnectionMarked { c ->
                    c.queryRows("PRAGMA foreign_key_list(`${table.tableName}`)") {
                        Triple(it.getText(2), it.getText(3), it.getText(6))
                    }
                }.filter { it.third == "CASCADE" }.associate { it.first to it.second }
                val expected = if (table.tableName in notCarried) emptyMap() else cascades
                assertEquals(table.tableName, expected, table.parents)
                for (parent in table.parents.keys) {
                    assertTrue("$parent must come before ${table.tableName}", tables.take(i).any { it.tableName == parent })
                }
            }
        }
    }
}
