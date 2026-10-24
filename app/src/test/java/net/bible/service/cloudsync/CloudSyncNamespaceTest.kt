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
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import okio.Path
import okio.Sink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
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
        var secretKey: String? = null
        override val signedIn = true
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            listedNames += name
            return if (name != null && name in existing) listOf(CloudFile("existing-$name", name, 0, 0, "")) else emptyList()
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
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = error("unused")
        override suspend fun delete(id: String) {}
    }

    private fun sync(packageName: String, adapter: CloudAdapter, namespace: String? = null): CloudSync {
        val realEnv = get<AppEnvironment>(AppEnvironment::class.java)
        val env = object : AppEnvironment by realEnv { override val packageName = packageName }
        return CloudSync(
            AppDialogController(), testCoreStrings(), env, testAppSettings(),
            object : CloudAdapterFactory {
                override val available = listOf("NEXT_CLOUD")
                override fun displayName(id: String) = id
                override fun create(id: String): CloudAdapter = adapter
            }, SyncScheduler { error("not scheduled") }, get(DocumentSyncStarter::class.java),
            DocumentSyncProvider { error("No document operations in this test") },
            get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java), namespace,
        ).also {
            val field = CloudSync::class.java.getDeclaredField("_adapter")
            field.isAccessible = true
            field.set(it, adapter)
        }
    }

    private fun accessor(): SyncableDatabaseAccessor<net.bible.android.database.BookmarkDatabase> {
        val file = File.createTempFile("bookmarks-ns-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        val db = DatabaseContainer.instance.getBookmarkDb(file.absolutePath).also { dbs += it }
        return SyncableDatabaseAccessor(db, { DatabaseContainer.instance.getBookmarkDb(it) }, { db }, file.path,
            SyncableDatabaseDefinition.BOOKMARKS, deviceId = "ns-device")
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

    @Test fun discreteWithOldFolderIsReset() = run {
        val adapter = FakeAdapter()
        val db = accessor().apply { seed() }
        sync("com.app.calculator", adapter).initializeSync(db)
        assertEquals(listOf<String?>("net.bible.android.activity-sync-bookmarks"), adapter.listedNames)
        // Nothing existed under the shared name, so a new folder was created and the old ids are gone.
        assertEquals(
            listOf("net.bible.android.activity-sync-bookmarks", get<AppEnvironment>(AppEnvironment::class.java).deviceIdentifier),
            adapter.createdFolders,
        )
        assertEquals("id-net.bible.android.activity-sync-bookmarks", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertNull(db.dao.getString("fakeSecret"))
        assertEquals(0L, db.dao.getLong(LAST_SYNCHRONIZED_KEY) ?: 0L)
        assertEquals("net.bible.android.activity", db.dao.getString(SYNC_NAMESPACE_KEY))
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
        assertNull(db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertNull(db.dao.getString(SYNC_NAMESPACE_KEY))
    }

    @Test fun iosReleaseUsesSharedName() {
        assertEquals("net.bible.android.activity-sync-bookmarks", sync("org.andbible.ios", FakeAdapter()).syncFolderName("bookmarks"))
    }

    @Test fun debugOverrideWins() {
        assertEquals("ns-x-sync-bookmarks", sync("com.app.calculator", FakeAdapter(), "ns-x").syncFolderName("bookmarks"))
    }

    @Test fun documentsFolderUsesSharedName() = run {
        val adapter = FakeAdapter()
        sync("com.app.calculator", adapter).documentsSyncFolderId()
        assertEquals(listOf<String?>("net.bible.android.activity-sync-documents"), adapter.listedNames)
        assertEquals(listOf("net.bible.android.activity-sync-documents"), adapter.createdFolders)
    }

    @Test fun disabledCategoryUntouched() = run {
        val adapter = FakeAdapter()
        val db = DatabaseContainer.instance.bookmarkDb.let { SyncableDatabaseDefinition.BOOKMARKS.accessor }
        db.seed()
        testAppSettings().setBoolean("sync_enable_bookmarks", false)
        sync("com.app.calculator", adapter).synchronize()
        assertTrue(adapter.listedNames.isEmpty())
        assertEquals("old-sync", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("secret", db.dao.getString("fakeSecret"))
        assertNull(db.dao.getString(SYNC_NAMESPACE_KEY))
    }
}
