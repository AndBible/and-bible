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

package net.bible.service.cloudsync.merge

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.INITIAL_BACKUP_FILENAME
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.LAST_SYNCHRONIZED_KEY
import net.bible.service.cloudsync.SYNC_DEVICE_FOLDER_FILE_ID_KEY
import net.bible.service.cloudsync.SYNC_FOLDER_FILE_ID_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.applyPatchesForDatabase
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.webdav.FakeDavServer
import net.bible.service.cloudsync.webdav.*
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.*
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The "Merge data" choice end to end through [CloudSync.synchronize], against the in-process WebDAV fake.
 *
 * Device B is the app's own [DatabaseContainer] (the real production path: `replacingDatabases`, the swap, the
 * second read-only connection of the natural-key re-key). Device A is a standalone bookmarks database on the bundled
 * driver whose data is put into the fake cloud by hand (initial file, patches) and which applies B's patch to see B's
 * rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MergeJoinFlowTest {
    private val dialogs: AppDialogController get() = get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val testDispatcher = StandardTestDispatcher()

    private val env get() = get<AppEnvironment>(AppEnvironment::class.java)
    private val settings get() = get<AppSettings>(AppSettings::class.java)
    private val cloud get() = get<CloudSync>(CloudSync::class.java)
    private val category = SyncableDatabaseDefinition.BOOKMARKS

    private val server = FakeDavServer(nowServer = System.currentTimeMillis())
    private val davState = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
    private val davUi = object : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = true
        override fun message(kind: WebDavMessage) = kind.name
    }
    private fun adapter(deviceId: String) = WebDavCloudAdapter(
        WebDavConfig("https://h/dav/", "me", "pw", "AndBible"), davState, davUi, deviceId = deviceId,
        clientFactory = { c, _ -> WebDavClient(createWebDavHttpClient(server.engine, c.username, c.password), c.serverUrl, c.username, c.password, { server.nowServer }) },
        nowMs = { server.nowServer },
    )

    private val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
    private var originalAdapter: Any? = null
    private val files = mutableListOf<File>()
    private val opened = mutableListOf<SyncableDatabaseAccessor<*>>()
    private val seenDialogs = mutableListOf<AppDialogRequest>()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase() // before setMain: the reset itself hops to Main
        Dispatchers.setMain(testDispatcher)
        originalAdapter = adapterField.get(cloud)
        category.setSyncEnabled(settings, true)
    }

    @After fun tearDown() {
        cloud.mergeStageHookForTest = null
        adapterField.set(cloud, originalAdapter)
        category.setSyncEnabled(settings, false)
        dialogs.cancelAll()
        Dispatchers.resetMain()
        opened.forEach { runCatching { it.localDb.close() } }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        DatabaseResetter.resetDatabase()
    }

    private val b: SyncableDatabaseAccessor<*> get() = DatabaseContainer.databaseAccessorsByCategory[category]!!
    private val bDao get() = (b.localDb as BookmarkDatabase).bookmarkDao()

    /** Device A: a standalone bookmarks database (journal mode TRUNCATE, as the other merge tests). */
    private suspend fun deviceA(): SyncableDatabaseAccessor<BookmarkDatabase> {
        val file = File.createTempFile("join-flow-a-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        fun open(path: String): BookmarkDatabase = Room.databaseBuilder(application, BookmarkDatabase::class.java, path)
            .addMigrations(*bookmarkMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, { open(it) }, { db.close(); db = open(file.absolutePath); db },
            file.path, category, deviceId = "device-A")
        createTriggers(dbDef)
        dbDef.localDb.useWriterConnectionMarked { }
        return dbDef.also { opened += it }
    }

    private suspend fun SyncableDatabaseAccessor<BookmarkDatabase>.addLabel(name: String) =
        BookmarkEntities.Label(name = name).also { localDb.bookmarkDao().insert(it) }

    private val folderName get() = "${env.packageName}-sync-${category.name.lowercase()}"

    /** A creates the sync folder as `CREATE_NEW` does: folder, device folder, initial file made from its database with an empty log. */
    private suspend fun publishFromA(a: SyncableDatabaseAccessor<BookmarkDatabase>, adapterA: WebDavCloudAdapter): String {
        val folder = adapterA.createNewFolder(folderName).id
        adapterA.createNewFolder("device-A", parentId = folder)
        a.dao.clearLog()
        val raw = File.createTempFile("initial-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        val gz = File.createTempFile("initial-", ".sqlite3.gz", CommonUtils.tmpDir).also { files += it }
        File(a.localDbPath).copyTo(raw, overwrite = true)
        ArchiveUtils.gzipFile(raw.toOkioPath(), gz.toOkioPath())
        adapterA.upload(INITIAL_BACKUP_FILENAME, gz.toOkioPath(), folder)
        return folder
    }

    private suspend fun signedInAdapters(): Pair<WebDavCloudAdapter, WebDavCloudAdapter> {
        val mine = adapter(env.deviceIdentifier)
        check(mine.signIn())
        adapterField.set(cloud, mine)
        val adapterA = adapter("device-A")
        check(adapterA.signIn())
        return mine to adapterA
    }

    /**
     * Runs [CloudSync.synchronize] and answers every dialog it raises: the options question with the choice at
     * [choiceIndex] (0 = Merge, 1 = Fetch, 2 = Create, 3 = Disable), confirmations with Ok, messages with Ok.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.syncAnswering(choiceIndex: Int) {
        val run = async(Dispatchers.IO) { cloud.synchronize() }
        val deadline = System.currentTimeMillis() + 60_000
        while (!run.isCompleted) {
            advanceUntilIdle()
            dialogs.pending.value?.let { shown ->
                seenDialogs += shown.request
                dialogs.respond(shown.id, when (val r = shown.request) {
                    is AppDialogRequest.Options -> AppDialogResult.Selected(r.options[choiceIndex].value)
                    else -> AppDialogResult.Ok
                })
            }
            check(System.currentTimeMillis() < deadline) { "sync did not finish; dialogs seen: $seenDialogs" }
            withContext(Dispatchers.Default) { delay(10) }
        }
        run.await()
    }

    /** Every row of the synced tables plus the sync bookkeeping tables, as sorted text (blobs quoted as hex). */
    private suspend fun dump(db: SyncableDatabaseAccessor<*>): Map<String, List<String>> = db.localDb.useWriterConnectionMarked { c ->
        val tables = db.tableDefinitions.map { it.tableName } + listOf("LogEntry", "SyncConfiguration", "SyncStatus")
        tables.associateWith { table ->
            val columns = c.usePrepared("SELECT name FROM pragma_table_info('$table')") { st -> buildList { while (st.step()) add(st.getText(0)) } }
            val row = columns.joinToString(" || '|' || ") { "quote(\"$it\")" }
            c.usePrepared("SELECT $row FROM \"$table\"") { st -> buildList { while (st.step()) add(st.getText(0)) } }.sorted()
        }
    }

    /** Whether the key is stored at all, whatever its value type (the long keys are invisible to `getString`). */
    private suspend fun config(key: String) = b.dao.getConfig(key)?.let { it.stringValue ?: it.longValue ?: it.booleanValue }

    private suspend fun labelNames(): Set<String> =
        bDao.allLabelsSortedByName().map { it.name }.filter { it.startsWith("t-") }.toSet()

    @Test fun mergeKeepsBothAndUploads() = runTest(testDispatcher, timeout = 120.seconds) {
        val a = deviceA()
        a.addLabel("t-from-a")
        val (mine, adapterA) = signedInAdapters()
        val folder = publishFromA(a, adapterA)
        val deviceLabel = BookmarkEntities.Label(name = "t-from-b").also { bDao.insert(it) }

        syncAnswering(choiceIndex = 0)

        val options = seenDialogs.filterIsInstance<AppDialogRequest.Options>().single().options.map { it.label }
        assertEquals(4, options.size)
        assertEquals("Merge data", options.first())
        assertTrue("both sets are kept on B", labelNames().containsAll(setOf("t-from-a", "t-from-b")))
        assertEquals(folder, config(SYNC_FOLDER_FILE_ID_KEY))
        assertNotNull(config(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertTrue(category.isSyncEnabled(settings))
        assertNotNull(config(LAST_PATCH_WRITTEN_KEY))

        // B's device folder holds a patch with B's rows; A applies it and sees them.
        val bFolder = mine.getFolders(folder).single { it.name == env.deviceIdentifier }
        val patch = adapterA.listFiles(parentsIds = listOf(bFolder.id)).single { it.name.endsWith(".sqlite3.gz") }
        val local = File.createTempFile("b-patch-", ".sqlite3.gz", CommonUtils.tmpDir).also { files += it }
        adapterA.download(patch.id, okio.FileSystem.SYSTEM.sink(local.toOkioPath()))
        applyPatchesForDatabase(a, env, local.toOkioPath())
        val aNames = a.localDb.bookmarkDao().allLabelsSortedByName().map { it.name }
        assertTrue("A sees B's label: $aNames", "t-from-b" in aNames)
        assertEquals("t-from-b", a.localDb.bookmarkDao().labelById(deviceLabel.id)?.name)
    }

    /** Review Focus 2 and 4: a failure after the merge (or after the patches) puts the device back exactly. */
    private suspend fun kotlinx.coroutines.test.TestScope.failingMergeRestoresDevice(stage: String) {
        val a = deviceA()
        a.addLabel("t-from-a")
        val (_, adapterA) = signedInAdapters()
        publishFromA(a, adapterA)
        bDao.insert(BookmarkEntities.Label(name = "t-from-b"))
        val before = dump(b)
        assertTrue("the device has rows to lose", before.getValue("Label").any { it.contains("t-from-b") })
        cloud.mergeStageHookForTest = { s -> if (s == stage) throw IllegalStateException("injected at $s") }

        syncAnswering(choiceIndex = 0)

        assertEquals("rows, LogEntry, SyncConfiguration and SyncStatus equal to before", before, dump(b))
        assertNull(config(SYNC_FOLDER_FILE_ID_KEY))
        assertNull(config(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertNull(config(LAST_SYNCHRONIZED_KEY))
        assertNull(config(LAST_PATCH_WRITTEN_KEY))
        assertFalse("category sync is off", category.isSyncEnabled(settings))
        assertEquals(setOf("t-from-b"), labelNames())
        assertTrue("the failure was reached", seenDialogs.any { it is AppDialogRequest.Options })
    }

    @Test fun failureAfterPatchesRestoresDevice() = runTest(testDispatcher, timeout = 120.seconds) { failingMergeRestoresDevice("afterPatches") }

    @Test fun failureAfterTheMergeRestoresDevice() = runTest(testDispatcher, timeout = 120.seconds) { failingMergeRestoresDevice("afterMerge") }

    @Test fun newerSchemaPatchDuringMergeRestoresAndAsksForUpdate() = runTest(testDispatcher, timeout = 120.seconds) {
        val a = deviceA()
        a.addLabel("t-from-a")
        val (_, adapterA) = signedInAdapters()
        val folder = publishFromA(a, adapterA)
        val aFolder = adapterA.getFolders(folder).single { it.name == "device-A" }
        val tmp = File.createTempFile("future-patch-", ".gz", CommonUtils.tmpDir).also { it.writeBytes(byteArrayOf(1)); files += it }
        adapterA.upload("1.9999.sqlite3.gz", tmp.toOkioPath(), aFolder.id)
        bDao.insert(BookmarkEntities.Label(name = "t-from-b"))
        val before = dump(b)

        syncAnswering(choiceIndex = 0)

        assertEquals(before, dump(b))
        assertNull(config(SYNC_FOLDER_FILE_ID_KEY))
        assertNull(config(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertNull(config(LAST_SYNCHRONIZED_KEY))
        assertNull(config(LAST_PATCH_WRITTEN_KEY))
        assertFalse(category.isSyncEnabled(settings))
        val messages = seenDialogs.filterIsInstance<AppDialogRequest.Message>()
        assertEquals("exactly one 'update the app' message", 1, messages.size)
        assertTrue(messages.single().message, messages.single().message.contains(net.bible.test.testCoreStrings().syncUpdateApp))
    }

    @Test fun noCloudFolderNoQuestion() = runTest(testDispatcher, timeout = 120.seconds) {
        val (_, adapterA) = signedInAdapters()
        bDao.insert(BookmarkEntities.Label(name = "t-from-b"))

        syncAnswering(choiceIndex = 0)

        assertTrue("no dialog: $seenDialogs", seenDialogs.isEmpty())
        assertEquals(setOf("t-from-b"), labelNames())
        assertNotNull(config(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals(1, adapterA.listFiles(name = folderName).size)
        assertTrue(category.isSyncEnabled(settings))
    }
}
