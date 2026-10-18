package net.bible.service.cloudsync.filetree

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.sharedcore.platform.cloud.FileTreeCloudStore
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class FileTreeCloudAdapterTest {
    private val fs = FakeFileSystem()
    private val root = "/cloud".toPath()
    private val tempDir = "/tmp".toPath()
    private var clock = 100L
    private var previousHost: net.bible.service.db.DatabaseHost? = null

    @BeforeTest
    fun setUp() {
        previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        fs.createDirectories(root)
        fs.createDirectories(tempDir)
    }

    /** [DatabaseContainer.installHost] is global; put back whatever was installed before the test. */
    @AfterTest
    fun tearDown() { previousHost?.let { DatabaseContainer.installHost(it) } }

    private fun adapter(signedIn: Boolean = true): FileTreeCloudAdapter {
        val store = FileTreeCloudStore(fs, { if (signedIn) root else null }, now = { clock++ })
        return FileTreeCloudAdapter(store, "dev-1", tempDir, fs)
    }

    private fun source(content: String): Path = "/source".toPath().also { fs.write(it) { writeUtf8(content) } }

    private suspend fun withDb(block: suspend (SyncableDatabaseAccessor<*>) -> Unit) {
        DatabaseContainer.installHost(FakeDatabaseHost(newTestDir()))
        val container = DatabaseContainer.create()
        try {
            block(DatabaseContainer.getDatabaseAccessorFactories(container).first().invoke())
        } finally {
            DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) }
        }
    }

    @Test fun signedOutStoreIsNotSignedIn() = runTest {
        val a = adapter(signedIn = false)
        assertFalse(a.signedIn)
        assertFalse(a.signIn())
    }

    @Test fun unknownFolderUntilMarked() = runTest(timeout = 10.minutes) {
        val a = adapter()
        val folder = a.createNewFolder("sync")
        withDb { db ->
            assertFalse(a.isSyncFolderKnown(db, "x", folder.id))
            a.makeSyncFolderKnown(db, "x", folder.id)
            assertTrue(a.isSyncFolderKnown(db, "x", folder.id))
            val names = a.listFiles(listOf(folder.id)).map { it.name }
            val marker = names.single()
            assertTrue(marker.startsWith("device-known-dev-1-"), marker)
            assertEquals(marker, db.dao.getString(FILETREE_SECRET_FILE_NAME_KEY))
            assertTrue(fs.list(tempDir).isEmpty(), "temp marker file removed")
        }
    }

    @Test fun markerDeletedRemotelyForgetsConfig() = runTest(timeout = 10.minutes) {
        val a = adapter()
        val folder = a.createNewFolder("sync")
        withDb { db ->
            a.makeSyncFolderKnown(db, "x", folder.id)
            a.delete(a.listFiles(listOf(folder.id)).single().id)
            assertFalse(a.isSyncFolderKnown(db, "x", folder.id))
            assertNull(db.dao.getConfig(FILETREE_SECRET_FILE_NAME_KEY))
        }
    }

    @Test fun getConfigsReturnsTheMarkerConfig() = runTest(timeout = 10.minutes) {
        val a = adapter()
        val folder = a.createNewFolder("sync")
        withDb { db ->
            assertTrue(a.getConfigs(db).isEmpty())
            a.makeSyncFolderKnown(db, "x", folder.id)
            assertEquals(listOf(FILETREE_SECRET_FILE_NAME_KEY), a.getConfigs(db).map { it.keyName })
        }
    }

    @Test fun delegatesStoreOperations() = runTest {
        val a = adapter()
        val folder = a.createNewFolder("sync")
        val uploaded = a.upload("patch.gz", source("hello"), folder.id)
        assertEquals("patch.gz", uploaded.name)
        assertEquals(listOf(uploaded.id), a.listFiles(listOf(folder.id), "patch.gz").map { it.id })
        assertTrue(a.listFiles(listOf(folder.id), createdTimeAtLeast = uploaded.createdTime + 1).isEmpty())
        assertEquals(listOf(uploaded.id), a.listFiles(listOf(folder.id), createdTimeAtLeast = uploaded.createdTime).map { it.id })
        val out = Buffer()
        a.download(uploaded.id, out)
        assertEquals("hello", out.readUtf8())
        assertNotNull(a.get(uploaded.id))
    }
}
