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

package net.bible.service.cloudsync.canonical

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.INITIAL_BACKUP_FILENAME
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import net.bible.service.cloudsync.webdav.FakeDavServer
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.service.db.importDatabaseFile
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.myDocumentId
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.*
import net.bible.service.cloudsync.webdav.*
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
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
 * #3906 Task 5: when the canonical-id heal runs. At open, after a cloud fetch, after a merge, after the failed-merge
 * restore and after an import, with the sync keys a fetched file carries reset first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CanonicalHealHooksTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val testDispatcher = StandardTestDispatcher()
    private val dialogs: AppDialogController get() = get(AppDialogController::class.java)
    private val env get() = get<AppEnvironment>(AppEnvironment::class.java)
    private val settings get() = get<AppSettings>(AppSettings::class.java)
    private val cloud get() = get<CloudSync>(CloudSync::class.java)
    private val category = SyncableDatabaseDefinition.MYDOCUMENTS
    private val docs get() = category.accessor
    private val docDao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
    private val files = mutableListOf<File>()

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
    private val seenDialogs = mutableListOf<AppDialogRequest>()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase() // before setMain: the reset itself hops to Main
        Dispatchers.setMain(testDispatcher)
        originalAdapter = adapterField.get(cloud)
    }

    @After fun tearDown() {
        cloud.mergeStageHookForTest = null
        DatabaseContainer.openHealInTests = false
        DatabaseContainer.openStepHookForTest = null
        adapterField.set(cloud, originalAdapter)
        category.setSyncEnabled(settings, false)
        dialogs.cancelAll()
        Dispatchers.resetMain()
        files.forEach { it.delete() }
        DatabaseResetter.resetDatabase()
    }

    private suspend fun addBook(initials: String = "AIDocuments", pageKey: String = "k"): Pair<MyDocument, MyDocumentPage> {
        val doc = docDao.documentByInitials(initials) ?: MyDocument(name = initials, initials = initials).also { docDao.insert(it) }
        val page = MyDocumentPage(documentId = doc.id, title = "t-$pageKey", pageKey = pageKey, orderNumber = 1).also { docDao.insert(it) }
        return doc to page
    }

    /** The current mydocuments database as a plain file (checkpointed, so the WAL is in it). */
    private suspend fun copyOfDb(): File {
        docs.localDb.useWriterConnectionMarked { it.exec("PRAGMA wal_checkpoint(FULL)") }
        return File.createTempFile("hooks-", ".sqlite3", CommonUtils.tmpDir).also { files += it; File(docs.localDbPath).copyTo(it, overwrite = true) }
    }

    private suspend fun config(key: String) = docs.dao.getConfig(key)?.let { it.stringValue ?: it.longValue ?: it.booleanValue }

    private suspend fun assertAllCanonical() {
        for (d in docDao.allDocuments()) assertEquals("id of ${d.initials}", canonicalMyDocumentId(d.initials), d.id)
    }

    private val initialFile = CloudFile("initial", INITIAL_BACKUP_FILENAME, 1L, 1L, "folder")

    /** The fetch's sync-state step on a downloaded file, through the real swap (as `fetchAndRestoreInitial` does it). */
    private suspend fun fetch(downloaded: File) {
        cloud.swapInInitialDb(docs, downloaded.toOkioPath()) {
            cloud.finishInitialFetch(docs, "folder", "device-folder", emptyList(), initialFile)
        }
    }

    @Test fun fetchOfAnOldCloudFileIsHealed() = runBlocking {
        val (doc, page) = addBook()
        val downloaded = copyOfDb()
        assertTrue("the fixture has a random id", doc.id != canonicalMyDocumentId("AIDocuments"))

        fetch(downloaded)

        assertAllCanonical()
        assertEquals(canonicalMyDocumentId("AIDocuments"), docDao.pagesForDocument(canonicalMyDocumentId("AIDocuments")).single { it.id == page.id }.documentId)
        assertEquals(1L, config(CANONICAL_IDS_VERSION_KEY))
        assertEquals(true, config(CANONICAL_HEAL_MOVED_ROWS_KEY))
        assertEquals("rows moved: the watermark is lowered so they are written again", 0L, config(LAST_PATCH_WRITTEN_KEY))
        assertEquals("folder", config(net.bible.service.cloudsync.SYNC_FOLDER_FILE_ID_KEY))
    }

    /** The cloud initial file is a whole database of another device: its own canonical state must not become ours. */
    @Test fun fetchedFileDoesNotBringTheOtherDevicesCanonicalState() = runBlocking {
        docDao.insert(MyDocument(id = canonicalMyDocumentId("AIDocuments"), name = "AI", initials = "AIDocuments"))
        docs.dao.setConfig(CANONICAL_IDS_VERSION_KEY, 1L)
        docs.dao.setConfig(CANONICAL_HEAL_MOVED_ROWS_KEY, true)
        docs.dao.setConfig(CANONICAL_MARKER_PENDING_KEY, true)
        docs.dao.setConfig("canonicalPeerSeen:X", 1L)
        docs.dao.setConfig("canonicalPeerSeen:Y", 1L)
        val downloaded = copyOfDb()

        fetch(downloaded)

        assertAllCanonical()
        assertNull("foreign peer record X", config("canonicalPeerSeen:X"))
        assertNull("foreign peer record Y", config("canonicalPeerSeen:Y"))
        assertEquals(1L, config(CANONICAL_IDS_VERSION_KEY))
        assertNull("this device moved nothing: the other device's flag must not stay", config(CANONICAL_HEAL_MOVED_ROWS_KEY))
        assertEquals("this device announces itself once", true, config(CANONICAL_MARKER_PENDING_KEY))
        assertNotNull("the watermark of this fetch is kept", config(LAST_PATCH_WRITTEN_KEY).takeIf { it != 0L })
    }

    @Test fun openHealsBeforeSettingsLoad() = runBlocking {
        val (doc, page) = addBook()
        assertTrue(doc.id != canonicalMyDocumentId("AIDocuments"))
        // Close the container without deleting its files, as a process restart would.
        DatabaseContainer.replacingDatabases { token -> DatabaseContainer.resetOwned(token) }
        val steps = mutableListOf<String>()
        DatabaseContainer.openHealInTests = true
        DatabaseContainer.openStepHookForTest = { steps += it }

        DatabaseContainer.instance // opens the container

        assertAllCanonical()
        assertEquals(page.id, docDao.pagesForDocument(canonicalMyDocumentId("AIDocuments")).single().id)
        val settingsLoad = steps.indexOf("settingsLoad")
        assertTrue("settings load is reached: $steps", settingsLoad >= 0)
        val heals = steps.filter { it.startsWith("heal:") }
        assertEquals("every synced database is healed: $steps", SyncableDatabaseDefinition.ALL.map { "heal:${it.name}" }.toSet(), heals.toSet())
        assertTrue("all heals before the settings load: $steps", steps.indexOfLast { it.startsWith("heal:") } < settingsLoad)
        assertEquals(1L, config(CANONICAL_IDS_VERSION_KEY))
    }

    /** A second open must not move anything or announce again: the heal is non-forced there. */
    @Test fun openIsIdempotent() = runBlocking {
        addBook()
        DatabaseContainer.replacingDatabases { token -> DatabaseContainer.resetOwned(token) }
        DatabaseContainer.openHealInTests = true
        DatabaseContainer.instance
        docs.dao.setConfig(CANONICAL_MARKER_PENDING_KEY, false)
        DatabaseContainer.replacingDatabases { token -> DatabaseContainer.resetOwned(token) }

        DatabaseContainer.instance

        assertEquals("not healed again, so no new marker", false, config(CANONICAL_MARKER_PENDING_KEY))
    }

    @Test fun importedRandomIdBookIsCanonical() = runBlocking {
        val (doc, _) = addBook("Imported")
        val file = copyOfDb()
        docs.localDb.useWriterConnectionMarked { it.exec("DELETE FROM MyDocumentPage"); it.exec("DELETE FROM MyDocument") }

        importDatabaseFile(category, file.toOkioPath())

        assertAllCanonical()
        assertEquals(1, docDao.allDocuments().size)
        assertTrue(doc.id != docDao.allDocuments().single().id)
    }

    /** A book registered before a forced heal keeps the old id; re-registering after it reads the canonical one. */
    @Test fun reregisterAfterAHealUsesTheCanonicalId() = runBlocking {
        val previous = MyDocumentBookManager.mainDispatcher
        MyDocumentBookManager.mainDispatcher = Dispatchers.Unconfined
        try {
            val (doc, _) = addBook()
            MyDocumentBookManager.registerDocument(doc)
            assertEquals(doc.id, MyDocumentBookManager.getBook("AIDocuments")!!.myDocumentId)

            assertEquals(1, healCanonicalIds(docs, force = true))
            MyDocumentBookManager.reregisterAllDocuments()

            assertEquals(canonicalMyDocumentId("AIDocuments"), MyDocumentBookManager.getBook("AIDocuments")!!.myDocumentId)
        } finally {
            MyDocumentBookManager.unregisterDocument("AIDocuments")
            MyDocumentBookManager.mainDispatcher = previous
        }
    }

    // ---- the merge and the failed-merge restore, end to end through CloudSync.synchronize ----

    private val folderName get() = "${env.packageName}-sync-${category.name.lowercase()}"

    private suspend fun signedInAdapters(): Pair<WebDavCloudAdapter, WebDavCloudAdapter> {
        val mine = adapter(env.deviceIdentifier)
        check(mine.signIn())
        adapterField.set(cloud, mine)
        val adapterA = adapter("device-A")
        check(adapterA.signIn())
        return mine to adapterA
    }

    /** Puts [raw] into the fake cloud as device A's initial file. */
    private suspend fun publish(adapterA: WebDavCloudAdapter, raw: File) {
        val folder = adapterA.createNewFolder(folderName).id
        adapterA.createNewFolder("device-A", parentId = folder)
        val gz = File.createTempFile("initial-", ".sqlite3.gz", CommonUtils.tmpDir).also { files += it }
        ArchiveUtils.gzipFile(raw.toOkioPath(), gz.toOkioPath())
        adapterA.upload(INITIAL_BACKUP_FILENAME, gz.toOkioPath(), folder)
    }

    private suspend fun TestScope.syncAnswering(choiceIndex: Int) {
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

    /** The cloud has an old device's AIDocuments (random id, page pA); this device has its own (random id, page pB). */
    private suspend fun divergedRandomIdBooks(): Pair<MyDocumentPage, MyDocumentPage> {
        val (_, pageA) = addBook(pageKey = "from-a")
        docs.dao.clearLog()
        val cloudFile = copyOfDb()
        docs.localDb.useWriterConnectionMarked { it.exec("DELETE FROM MyDocumentPage"); it.exec("DELETE FROM MyDocument") }
        val (_, pageB) = addBook(pageKey = "from-b")
        val (_, adapterA) = signedInAdapters()
        publish(adapterA, cloudFile)
        category.setSyncEnabled(settings, true)
        return pageA to pageB
    }

    @Test fun mergeResultIsCanonical() = runTest(testDispatcher, timeout = 120.seconds) {
        val (pageA, pageB) = divergedRandomIdBooks()

        syncAnswering(choiceIndex = 0)

        assertAllCanonical()
        val pages = docDao.pagesForDocument(canonicalMyDocumentId("AIDocuments")).map { it.id }.toSet()
        assertEquals("the cloud's page and the device's page are both under the canonical book", setOf(pageA.id, pageB.id), pages)
        assertEquals(1L, config(CANONICAL_IDS_VERSION_KEY))
    }

    @Test fun failedMergeRestoreIsHealed() = runTest(testDispatcher, timeout = 120.seconds) {
        val (_, pageB) = divergedRandomIdBooks()
        cloud.mergeStageHookForTest = { s -> if (s == "afterPatches") throw IllegalStateException("injected at $s") }

        syncAnswering(choiceIndex = 0)

        assertAllCanonical()
        assertEquals(setOf(pageB.id), docDao.pagesForDocument(canonicalMyDocumentId("AIDocuments")).map { it.id }.toSet())
        assertNull("the failed join forgot its sync state", config(net.bible.service.cloudsync.SYNC_FOLDER_FILE_ID_KEY))
    }
}
