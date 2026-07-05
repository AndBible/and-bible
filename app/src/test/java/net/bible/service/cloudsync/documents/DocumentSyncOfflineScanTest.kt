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

package net.bible.service.cloudsync.documents

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.CachedCloudDocument
import net.bible.android.database.SyncConfiguration
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudFile
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DownloadProgressListener
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.db.DatabaseContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Regression test for OSTicket 3362: opening the document-sync management view while signed in but
 * offline crashed the app. [DocumentSync.scan] refreshes the listing cache from the network, and an
 * IOException from that refresh (device offline) must be swallowed so the view falls back to the
 * cached listing instead of propagating a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncOfflineScanTest {
    @After
    fun tearDown() {
        setAdapter(null)
    }

    /** A signed-in cloud adapter whose network listing always fails, simulating an offline device. */
    private class OfflineCloudAdapter : CloudAdapter {
        override val signedIn: Boolean = true
        override suspend fun listFiles(
            parentsIds: List<String>?,
            name: String?,
            mimeType: String?,
            createdTimeAtLeast: Long?
        ): List<CloudFile> = throw IOException("NetworkError")

        override suspend fun signIn(activity: ActivityBase): Boolean = throw NotImplementedError()
        override suspend fun signOut() = throw NotImplementedError()
        override suspend fun get(id: String): CloudFile = throw NotImplementedError()
        override suspend fun getFolders(parentId: String): List<CloudFile> = throw NotImplementedError()
        override suspend fun download(id: String, outputStream: OutputStream, onProgress: DownloadProgressListener?) = throw NotImplementedError()
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = throw NotImplementedError()
        override suspend fun upload(name: String, file: File, parentId: String): CloudFile = throw NotImplementedError()
        override suspend fun delete(id: String) = throw NotImplementedError()
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean = throw NotImplementedError()
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = throw NotImplementedError()
        override fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> = throw NotImplementedError()
    }

    /** Injects (or clears) the private CloudSync adapter singleton for the test. */
    private fun setAdapter(adapter: CloudAdapter?) {
        val field = CloudSync::class.java.getDeclaredField("_adapter")
        field.isAccessible = true
        field.set(CloudSync, adapter)
    }

    @Test
    fun offlineScanFallsBackToCacheInsteadOfCrashing() = runBlocking {
        DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().replaceAll(
            listOf(CachedCloudDocument("KJV", "King James Version", "SWORD", "1.0", 10, "en", "BIBLE", "dev", 1L, null, false))
        )
        setAdapter(OfflineCloudAdapter())

        // Before the fix this threw IOException (uncaught -> crash); it must now return the cache.
        val items = DocumentSync.scan()

        assertTrue("Offline scan must return the cached listing", items.any { it.initials == "KJV" })
        assertEquals("King James Version", items.first { it.initials == "KJV" }.name)
    }
}
