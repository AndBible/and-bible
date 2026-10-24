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

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SyncConfiguration
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Sink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Release distributions share one sync folder per category ([SyncNamespace]); `initializeSync` resets a
 * category whose stored folder belongs to another namespace and leaves a standard upgrade untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudSyncNamespaceTest {
    private val files = mutableListOf<File>()
    private val dbs = mutableListOf<net.bible.android.database.BookmarkDatabase>()

    @After
    fun tearDown() {
        dbs.forEach { runCatching { it.close() } }
        files.forEach { it.delete() }
        testAppSettings().setBoolean("sync_enable_bookmarks", false)
        DatabaseResetter.resetDatabase()
    }

    /** Records folder lookups and creations; `existing` are the folder names the "cloud" already has. */
    private class FakeAdapter(private val existing: Set<String> = emptySet()) : CloudAdapter {
        val listedNames = mutableListOf<String?>()
        val createdFolders = mutableListOf<String>()
        var folderKnown = true
        /** Bytes served by [download] (a gzipped database), for the FETCH_INITIAL path. */
        var initialFileBytes: ByteArray? = null
        override val signedIn = true
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            listedNames += name
            return when {
                name != null && name in existing -> listOf(CloudFile("existing-$name", name, 0, 0, ""))
                name == INITIAL_BACKUP_FILENAME && initialFileBytes != null -> listOf(CloudFile("initial-id", name, 1, 1, ""))
                else -> emptyList()
            }
        }
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
            createdFolders += name
            return CloudFile("id-$name", name, 0, 0, parentId ?: "")
        }
        override suspend fun upload(name: String, file: Path, parentId: String) = CloudFile("up-$name", name, 1, 1, parentId)
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = folderKnown
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> =
            listOfNotNull(dbDef.dao.getConfig("fakeSecret"))
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun getFolders(parentId: String): List<CloudFile> = emptyList()
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) {
            sink.write(okio.Buffer().write(initialFileBytes!!), initialFileBytes!!.size.toLong())
            sink.flush(); sink.close()
        }
        override suspend fun delete(id: String) {}
    }

    /** Builds a CloudSync whose factory hands out [adapter]; [signedIn] sync goes through the real `signIn()`. */
    private suspend fun sync(packageName: String, adapter: CloudAdapter, namespace: String? = null, dialogs: AppDialogController = AppDialogController()): CloudSync {
        val realEnv = get<AppEnvironment>(AppEnvironment::class.java)
        val env = object : AppEnvironment by realEnv { override val packageName = packageName }
        return CloudSync(
            dialogs, testCoreStrings(), env, testAppSettings(),
            object : CloudAdapterFactory {
                override val available = listOf("NEXT_CLOUD")
                override fun displayName(id: String) = id
                override fun create(id: String): CloudAdapter = adapter
            }, SyncScheduler { error("not scheduled") }, get(DocumentSyncStarter::class.java),
            DocumentSyncProvider { error("No document operations in this test") },
            get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java), namespace,
        ).also { assertEquals(true, it.signIn()) }
    }

    private fun accessor(): SyncableDatabaseAccessor<net.bible.android.database.BookmarkDatabase> {
        val file = File.createTempFile("bookmarks-ns-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        var db = DatabaseContainer.instance.getBookmarkDb(file.absolutePath).also { dbs += it }
        // The reset lambda reopens the file: the FETCH path swaps the database file and needs a live db afterwards.
        return SyncableDatabaseAccessor(db, { DatabaseContainer.instance.getBookmarkDb(it) },
            { db = DatabaseContainer.instance.getBookmarkDb(file.absolutePath).also { dbs += it }; db }, file.path,
            SyncableDatabaseDefinition.BOOKMARKS, deviceId = "ns-device")
    }

    /** A gzipped bookmarks database with an empty config table: what another device uploads as `initial.sqlite3.gz`. */
    private fun gzippedEmptyDb(): ByteArray {
        val src = File.createTempFile("bookmarks-src-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        DatabaseContainer.instance.getBookmarkDb(src.absolutePath).close()
        val gz = File.createTempFile("bookmarks-src-", ".gz", CommonUtils.tmpDir).also { files += it }
        net.bible.service.common.ArchiveUtils.gzipFile(src.toOkioPath(), gz.toOkioPath())
        return gz.readBytes()
    }

    /**
     * Runs [block] off the test thread while answering the first-join dialogs: [firstAnswer] picks an option
     * (0 = fetch, 1 = create, 2 = disable) or cancels (null); a confirmation, if any, is accepted.
     * Returns the requests seen.
     */
    private suspend fun answeringDialogs(dialogs: AppDialogController, firstAnswer: Int?, block: suspend () -> Unit): List<AppDialogRequest> {
        val seen = mutableListOf<AppDialogRequest>()
        val job: kotlinx.coroutines.Deferred<Unit> = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).async {
            try { block() } catch (e: CancelStartedSync) { /* cancelled first join */ }
        }
        try {
            withTimeout(15_000) {
                while (!job.isCompleted) {
                    shadowOf(Looper.getMainLooper()).idle()
                    dialogs.pending.value?.let { p ->
                        seen += p.request
                        val r = p.request
                        dialogs.respond(p.id, when {
                            r is AppDialogRequest.Options && firstAnswer != null -> AppDialogResult.Selected(r.options[firstAnswer].value)
                            r is AppDialogRequest.Options -> AppDialogResult.Cancel
                            else -> AppDialogResult.Ok
                        })
                    }
                    Thread.sleep(20)
                }
                job.await()
            }
        } finally {
            // A timeout must not leave the job touching DatabaseContainer after teardown.
            job.cancel()
        }
        return seen
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun SyncableDatabaseAccessor<*>.seed(syncId: String = "old-sync", ns: String? = null) {
        dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, syncId)
        dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "old-device")
        dao.setConfig(LAST_SYNCHRONIZED_KEY, 123L)
        dao.setConfig(LAST_PATCH_WRITTEN_KEY, 456L)
        dao.setConfig("fakeSecret", "secret")
        ns?.let { dao.setConfig(SYNC_NAMESPACE_KEY, it) }
    }

    @Test fun standardKeepsItsFolderAndState() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed() }
        sync("net.bible.android.activity", adapter).initializeSync(db)
        assertTrue(adapter.listedNames.isEmpty())
        assertTrue(adapter.createdFolders.isEmpty())
        assertEquals("old-sync", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("old-device", db.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertEquals(123L, db.dao.getLong(LAST_SYNCHRONIZED_KEY))
        assertEquals("secret", db.dao.getString("fakeSecret"))
        assertEquals("net.bible.android.activity", db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    private suspend fun assertResetKeysGone(db: SyncableDatabaseAccessor<*>) {
        assertNull(db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertNull(db.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertNull(db.dao.getLong(LAST_SYNCHRONIZED_KEY))
        assertNull(db.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        assertNull(db.dao.getString("fakeSecret"))
    }

    @Test fun discreteWithOldFolderIsReset() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed() }
        val cloudSync = sync("com.app.calculator", adapter).also { it.initializeSync(db) }
        assertEquals(listOf<String?>("net.bible.android.activity-sync-bookmarks"), adapter.listedNames)
        // Nothing existed under the shared name, so a new folder was created and the old ids are gone.
        assertEquals(
            listOf("net.bible.android.activity-sync-bookmarks", cloudSync.syncDeviceName),
            adapter.createdFolders,
        )
        assertEquals("id-net.bible.android.activity-sync-bookmarks", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertNull(db.dao.getString("fakeSecret"))
        assertNull(db.dao.getLong(LAST_SYNCHRONIZED_KEY))
        assertEquals("net.bible.android.activity", db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    @Test fun discreteWithOldFolderAndExistingSharedFolderAsksTheFirstJoinQuestion() = run {
        val adapter = FakeAdapter(existing = setOf("net.bible.android.activity-sync-bookmarks"))
        val dialogs = AppDialogController()
        val db = accessor().apply { seed() }
        val cloudSync = sync("com.app.calculator", adapter, dialogs = dialogs)
        val seen = answeringDialogs(dialogs, firstAnswer = null) { cloudSync.initializeSync(db) }
        assertTrue("first-join question expected, saw $seen", seen.any { it is AppDialogRequest.Options })
        assertEquals(listOf<String?>("net.bible.android.activity-sync-bookmarks"), adapter.listedNames)
        assertTrue(adapter.createdFolders.isEmpty())
        assertResetKeysGone(db)
        assertNull(db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    @Test fun storedNamespaceMatchesNoReset() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed(ns = "net.bible.android.activity") }
        sync("com.app.calculator", adapter).initializeSync(db)
        assertTrue(adapter.listedNames.isEmpty())
        assertEquals("old-sync", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals(456L, db.dao.getLong(LAST_PATCH_WRITTEN_KEY))
    }

    @Test fun storedDifferentNamespaceIsReset() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed(ns = "net.bible.android.activity") }
        sync("com.app.calculator", adapter, namespace = "ns-x").initializeSync(db)
        assertEquals(listOf<String?>("ns-x-sync-bookmarks"), adapter.listedNames)
        assertEquals("ns-x", db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    /**
     * The FETCH swap replaces the whole database file, config table included, with the uploader's initial file,
     * which can lack the namespace key. The key must be written again afterwards, or the next sync of a
     * non-standard package would reset and ask the first-join question a second time.
     */
    @Test fun namespaceKeySurvivesAFetchInitialSwap() = run {
        val adapter = FakeAdapter(existing = setOf("net.bible.android.activity-sync-bookmarks")).apply {
            folderKnown = false
            initialFileBytes = gzippedEmptyDb()
        }
        val dialogs = AppDialogController()
        val db = accessor().apply { seed(ns = "net.bible.android.activity") }
        val cloudSync = sync("com.app.calculator", adapter, dialogs = dialogs)
        val seen = answeringDialogs(dialogs, firstAnswer = 0) { cloudSync.initializeSync(db) }
        assertTrue("fetch question expected, saw $seen", seen.any { it is AppDialogRequest.Options })
        assertEquals("existing-net.bible.android.activity-sync-bookmarks", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("net.bible.android.activity", db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    @Test fun discreteGetsItsOwnDeviceFolderName() = run {
        val raw = get<AppEnvironment>(AppEnvironment::class.java).deviceIdentifier
        val standardAdapter = FakeAdapter()
        val discreteAdapter = FakeAdapter()
        sync("net.bible.android.activity", standardAdapter).initializeSync(accessor())
        sync("com.app.calculator", discreteAdapter).initializeSync(accessor())
        val standardDevice = standardAdapter.createdFolders.last()
        val discreteDevice = discreteAdapter.createdFolders.last()
        assertEquals(raw, standardDevice)
        assertTrue("discrete device folder must differ: $discreteDevice", discreteDevice != standardDevice)
        assertTrue(discreteDevice.startsWith("$raw-"))
    }

    @Test fun discreteDeviceNameHidesThePackage() = run {
        val name = sync("com.app.calculator", FakeAdapter()).syncDeviceName
        assertFalse(name, name.contains("calculator", ignoreCase = true))
        assertEquals(name, sync("com.app.calculator", FakeAdapter()).syncDeviceName)
    }

    @Test fun suffixedAndUnknownIdsKeepTheRawDeviceName() = run {
        val raw = get<AppEnvironment>(AppEnvironment::class.java).deviceIdentifier
        for (pkg in listOf("net.bible.android.activity.debug", "org.example.other")) {
            assertEquals(pkg, raw, sync(pkg, FakeAdapter()).syncDeviceName)
        }
    }

    @Test fun createNewWritesTheStatusRowUnderTheDeviceName() = run {
        val db = accessor().apply { seed() }
        val cloudSync = sync("com.app.calculator", FakeAdapter())
        cloudSync.initializeSync(db)
        val statuses = db.dao.allSyncStatus()
        assertEquals(listOf(cloudSync.syncDeviceName), statuses.map { it.sourceDevice })
        assertTrue(cloudSync.syncDeviceName != get<AppEnvironment>(AppEnvironment::class.java).deviceIdentifier)
    }

    @Test fun suffixedBuildStaysSeparate() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed() }
        sync("net.bible.android.activity.debug", adapter).initializeSync(db)
        // Old name of a debug build was its package name, which is the new namespace: nothing resets.
        assertTrue(adapter.listedNames.isEmpty())
        assertEquals("old-sync", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("net.bible.android.activity.debug", db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    @Test fun cancelledFirstJoinLeavesTheKeyUnset() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed() }
        try {
            sync("com.app.calculator", adapter).initializeSync(db, allowInteraction = false)
        } catch (e: CancelStartedSync) { /* expected */ }
        assertResetKeysGone(db)
        assertNull(db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    @Test fun iosReleaseUsesSharedName() = run {
        assertEquals("net.bible.android.activity-sync-bookmarks", sync("org.andbible.ios", FakeAdapter()).syncFolderName("bookmarks"))
    }

    @Test fun debugOverrideWins() = run {
        assertEquals("ns-x-sync-bookmarks", sync("com.app.calculator", FakeAdapter(), "ns-x").syncFolderName("bookmarks"))
    }

    @Test fun documentsFolderUsesSharedName() = run {
        val adapter = FakeAdapter()
        sync("com.app.calculator", adapter).documentsSyncFolderId()
        assertEquals(listOf<String?>("net.bible.android.activity-sync-documents"), adapter.listedNames)
        assertEquals(listOf("net.bible.android.activity-sync-documents"), adapter.createdFolders)
    }

    /** The real bookmarks accessor, after the container has opened its database. */
    private fun containerAccessor(): SyncableDatabaseAccessor<*> {
        DatabaseContainer.instance.bookmarkDb // make sure the container database is open before taking its accessor
        return SyncableDatabaseDefinition.BOOKMARKS.accessor
    }

    @Test fun disabledCategoryUntouched() = run {
        val adapter = FakeAdapter()
        val db = containerAccessor().also { it.seed() }
        testAppSettings().setBoolean("sync_enable_bookmarks", false)
        sync("com.app.calculator", adapter).synchronize()
        assertTrue(adapter.listedNames.isEmpty())
        assertEquals("old-sync", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("secret", db.dao.getString("fakeSecret"))
        assertNull(db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    /** Positive control for [disabledCategoryUntouched]: the same setup with the category on does reach the adapter. */
    @Test fun enabledCategoryIsReset() = run {
        val adapter = FakeAdapter()
        val db = containerAccessor().also { it.seed() }
        testAppSettings().setBoolean("sync_enable_bookmarks", true)
        sync("com.app.calculator", adapter).synchronize()
        assertTrue(adapter.listedNames.contains("net.bible.android.activity-sync-bookmarks"))
        assertFalse(db.dao.getString(SYNC_FOLDER_FILE_ID_KEY) == "old-sync")
    }
}
