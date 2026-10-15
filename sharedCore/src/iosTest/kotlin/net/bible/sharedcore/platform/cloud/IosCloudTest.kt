package net.bible.sharedcore.platform.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosCloudTest {
    @Test fun invalidContainerCompletesWithoutAccount() = runTest {
        val container = IosUbiquityContainer("iCloud.invalid.i1test")
        container.prefetch()
        // A real dispatcher gives the native resolver a bounded wall-clock wait, not virtual time.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { assertNull(container.awaitReady()) }
        }
        assertNull(container.root())
        assertNull(container.awaitReady())
        assertFalse(iCloudDriveStore(container).signedIn)
    }

    @Test fun coordinatedLocalStoreRoundTrip() = runTest {
        withContext(Dispatchers.IO) {
            inTemporaryDirectory { directory ->
                val source = directory / "source"
                val root = directory / "cloud"
                FileSystem.SYSTEM.createDirectory(root)
                val bytes = ByteArray(130_001) { (it % 251).toByte() }
                FileSystem.SYSTEM.write(source) { write(bytes) }
                val store = FileTreeCloudStore(FileSystem.SYSTEM, { root }, IosFileCoordination(), now = { 123L })
                val uploaded = store.upload("backup.zip", source, "")
                assertEquals(listOf(uploaded.id), store.listFiles(listOf(""), null, null, null).map { it.id })
                val output = Buffer()
                store.download(uploaded.id, output, null)
                assertTrue(bytes.contentEquals(output.readByteArray()))
                store.delete(uploaded.id)
                assertTrue(store.listFiles(listOf(""), null, null, null).isEmpty())
            }
        }
    }

    @Test fun ordinaryFileNeedsNoDownloadEvenWithZeroTimeout() = runTest {
        withContext(Dispatchers.IO) {
            inTemporaryDirectory { directory ->
                val file = directory / "local"
                FileSystem.SYSTEM.write(file) { writeUtf8("local") }
                IosFileCoordination().ensureDownloaded(file, 0)
            }
        }
    }

    @Test fun missingFileDownloadStatusSurfacesFailure() = runTest {
        withContext(Dispatchers.IO) {
            inTemporaryDirectory { directory ->
                var caught: Throwable? = null
                try { IosFileCoordination().ensureDownloaded(directory / "missing", 0) }
                catch (error: Throwable) { caught = error }
                assertTrue(caught is okio.FileNotFoundException)
            }
        }
    }

    @Test fun missingStoreDownloadClosesOwnedSinkIncludingDeletedParent() = runTest {
        withContext(Dispatchers.IO) {
            inTemporaryDirectory { directory ->
                val root = directory / "cloud"
                FileSystem.SYSTEM.createDirectory(root)
                val store = FileTreeCloudStore(FileSystem.SYSTEM, { root }, IosFileCoordination(), now = { 1L })
                store.createNewFolder("parent")
                store.delete("parent")
                for (id in listOf("missing", "parent/missing")) {
                    var closed = false
                    val sink = object : okio.Sink by Buffer() {
                        override fun close() { closed = true }
                    }
                    kotlin.test.assertFailsWith<okio.FileNotFoundException> { store.download(id, sink) }
                    assertTrue(closed)
                }
            }
        }
    }

    @Test fun coordinatedReadPreservesNullableResult() = runTest {
        withContext(Dispatchers.IO) {
            inTemporaryDirectory { directory ->
                val file = directory / "local"
                FileSystem.SYSTEM.write(file) { writeUtf8("local") }
                assertNull(IosFileCoordination().read<String?>(file) { null })
            }
        }
    }

    @Test fun coordinatedAccessorsRethrowOriginalFailure() = runTest {
        withContext(Dispatchers.IO) {
            inTemporaryDirectory { directory ->
                val file = directory / "local"
                FileSystem.SYSTEM.write(file) { writeUtf8("local") }
                val expected = IllegalStateException("accessor failure")
                val coordinator = IosFileCoordination()
                for (read in listOf(true, false)) {
                    var caught: Throwable? = null
                    try {
                        if (read) coordinator.read(file) { throw expected }
                        else coordinator.write(file) { throw expected }
                    } catch (error: Throwable) { caught = error }
                    assertSame(expected, caught)
                }
            }
        }
    }

    private suspend fun inTemporaryDirectory(block: suspend (Path) -> Unit) {
        val directory = NSTemporaryDirectory().toPath() / "i1-cloud-${NSUUID().UUIDString}"
        FileSystem.SYSTEM.createDirectory(directory)
        try { block(directory) }
        finally { FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false) }
    }
}
