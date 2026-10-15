package net.bible.sharedcore.platform.cloud

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileNotFoundException
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class FileTreeCloudStoreTest {
    private val fs = FakeFileSystem()
    private val root = "/cloud".toPath()
    private var clock = 100L
    private val store = FileTreeCloudStore(fs, { root }, now = { clock++ })

    init { fs.createDirectories(root) }

    private fun source(content: String): Path = "/source".toPath().also {
        fs.write(it) { writeUtf8(content) }
    }

    @Test fun trustedRootAliasIsCanonicalButInnerLinksAreRejected() = runTest {
        fs.allowSymlinks = true
        val canonical = "/private/var/mobile/cloud".toPath()
        fs.createDirectories(canonical)
        fs.createSymlink("/var".toPath(), "/private/var".toPath())
        val paths = mutableListOf<Path>()
        val coordination = object : FileCoordination by FileCoordination.PassThrough {
            override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T): T {
                paths += path
                return block(path)
            }
        }
        val aliased = FileTreeCloudStore(fs, { "/var/mobile/cloud".toPath() }, coordination, now = { clock++ })
        aliased.upload("safe", source("content"), "")
        assertEquals(listOf(canonical / "safe"), paths)
        val output = Buffer()
        aliased.download("safe", output)
        assertEquals("content", output.readUtf8())
        fs.createSymlink(canonical / "inner", canonical)
        assertFailsWith<IOException> { aliased.download("inner/safe", Buffer()) }
        assertFailsWith<IOException> { aliased.createNewFolder("child", "inner") }
        assertFailsWith<IOException> { aliased.listFiles() }
        assertFailsWith<IOException> { aliased.delete("") }
        assertTrue(fs.exists(canonical / "safe"))
    }

    @Test fun signedOutOperationsFail() = runTest {
        val signedOut = FileTreeCloudStore(fs, { null }, now = { clock++ })
        assertFalse(signedOut.signedIn)
        assertFalse(signedOut.signIn())
        signedOut.signOut()
        val operations: List<suspend () -> Unit> = listOf(
            { signedOut.listFiles() }, { signedOut.get("") }, { signedOut.getFolders("") },
            { signedOut.download("x", Buffer()) }, { signedOut.createNewFolder("x") },
            { signedOut.upload("x", source("x"), "") }, { signedOut.delete("x") }
        )
        operations.forEach { operation ->
            assertEquals("not signed in", assertFailsWith<IOException> { operation() }.message)
        }
    }

    @Test fun nestedFoldersAndFilters() = runTest {
        assertTrue(store.signIn())
        store.signOut()
        assertTrue(store.signedIn)
        store.createNewFolder("a")
        store.createNewFolder("b", "a")
        val uploaded = store.upload("x.gz", source("content"), "a/b")
        assertEquals(7, uploaded.size)
        assertEquals("a/b", uploaded.parentId)
        assertEquals("x.gz", uploaded.name)
        assertEquals(uploaded, store.get("a/b/x.gz"))
        assertEquals(listOf(uploaded), store.listFiles(listOf("a/b")))
        assertTrue(store.listFiles(listOf("a")).isEmpty())
        assertEquals(listOf(uploaded), store.listFiles())
        assertTrue(store.listFiles(name = "y").isEmpty())
        for (mime in listOf("application/gzip", "application/x-gzip")) {
            assertEquals(listOf(uploaded), store.listFiles(mimeType = mime))
        }
        assertTrue(store.listFiles(mimeType = "application/zip").isEmpty())
        assertTrue(store.listFiles(mimeType = "unknown/type").isEmpty())
        assertTrue(store.listFiles(createdTimeAtLeast = uploaded.createdTime + 1).isEmpty())
        assertEquals(listOf(uploaded), store.listFiles(createdTimeAtLeast = uploaded.createdTime))
        assertEquals(listOf("b"), store.getFolders("a").map { it.name })
        assertEquals(0, store.get("a").size)
        assertEquals("", store.get("").parentId)
    }

    @Test fun mimeAndOrdering() = runTest {
        for (name in listOf("z.zip", "a.sqlite3", "b.gz", "other.txt")) {
            store.upload(name, source(name), "")
        }
        assertEquals(listOf("z.zip"), store.listFiles(mimeType = "application/zip").map { it.id })
        assertEquals(listOf("a.sqlite3"), store.listFiles(mimeType = "application/x-sqlite3").map { it.id })
        val files = store.listFiles()
        assertEquals(files.sortedWith(compareBy<CloudFile> { it.createdTime }.thenBy { it.id }), files)
        assertEquals(files.distinct(), store.listFiles(listOf("", "")))
    }

    @Test fun downloadProgressAndCoordination() = runTest {
        val calls = mutableListOf<String>()
        val coordination = object : FileCoordination {
            override fun <T> read(path: Path, block: (Path) -> T): T {
                calls += "read"; return block(path)
            }
            override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T): T {
                calls += "write"; return block(path)
            }
            override suspend fun ensureDownloaded(path: Path, timeoutMs: Long) {
                assertEquals(60_000L, timeoutMs); calls += "download"
            }
        }
        val coordinated = FileTreeCloudStore(fs, { root }, coordination, now = { clock++ })
        val content = "a".repeat(200 * 1024)
        coordinated.upload("large.gz", source(content), "")
        val progress = mutableListOf<Long>()
        val sink = Buffer()
        coordinated.download("large.gz", sink) { progress += it }
        assertEquals(content, sink.readUtf8())
        assertTrue(progress.size >= 4)
        assertTrue(progress.zipWithNext().all { (a, b) -> a < b })
        assertEquals(content.length.toLong(), progress.last())
        assertEquals(listOf("write", "download", "read"), calls)
    }

    @Test fun everyMutationIsCoordinatedAndCoordinationFailurePreventsMutation() = runTest {
        val paths = mutableListOf<Path>()
        var failure: IOException? = null
        val coordination = object : FileCoordination by FileCoordination.PassThrough {
            override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T): T {
                failure?.let { throw it }
                paths += path
                return block(path)
            }
        }
        val coordinated = FileTreeCloudStore(fs, { root }, coordination, now = { clock++ })
        coordinated.createNewFolder("folder")
        coordinated.upload("file", source("content"), "folder")
        coordinated.delete("folder")
        assertEquals(listOf(root / "folder", root / "folder/file", root / "folder"), paths)
        failure = IOException("coordination denied")
        assertSame(failure, assertFailsWith<IOException> { coordinated.createNewFolder("denied") })
        assertFalse(fs.exists(root / "denied"))
        store.upload("retained", source("original"), "")
        assertSame(failure, assertFailsWith<IOException> { coordinated.delete("retained") })
        assertEquals("original", fs.read(root / "retained") { readUtf8() })
    }

    @Test fun commonStoreKeepsCurrentAndCoordinatesConflictCleanup() = runTest {
        store.upload("file", source("current"), "")
        val events = mutableListOf<String>()
        val coordination = object : FileCoordination by FileCoordination.PassThrough {
            override fun hasConflicts(path: Path): Boolean { events += "detect"; return true }
            override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T): T {
                assertEquals(FileCoordination.Mutation.UPDATE, operation)
                events += "begin"
                return block(path).also { events += "end" }
            }
            override fun discardOtherVersions(path: Path) { events += "discard" }
        }
        val output = Buffer()
        FileTreeCloudStore(fs, { root }, coordination, now = { clock++ }).download("file", output)
        assertEquals("current", output.readUtf8())
        assertEquals(listOf("detect", "begin", "discard", "end"), events)
    }

    @Test fun missingItemsAndParents() = runTest {
        assertFailsWith<FileNotFoundException> { store.get("missing") }
        assertFailsWith<FileNotFoundException> { store.download("missing", Buffer()) }
        assertFailsWith<FileNotFoundException> { store.delete("missing") }
        assertTrue(store.listFiles(listOf("missing")).isEmpty())
        assertTrue(store.getFolders("missing").isEmpty())
        assertFailsWith<FileNotFoundException> { store.upload("x", source("x"), "missing") }
        assertFalse(fs.exists(root / "missing"))
    }

    @Test fun replacementDeletionAndDeviceKnownRoundTrip() = runTest {
        store.createNewFolder("a")
        store.upload("x.gz", source("old"), "a")
        store.upload("x.gz", source("new"), "a")
        assertEquals("new", fs.read(root / "a/x.gz") { readUtf8() })
        assertFalse(fs.list(root / "a").any { ".tmp-" in it.name })
        store.upload("device-known-abc", source("known"), "a")
        assertEquals("device-known-abc", store.get("a/device-known-abc").name)
        store.delete("a/device-known-abc")
        assertFailsWith<FileNotFoundException> { store.get("a/device-known-abc") }
        store.delete("a")
        assertFalse(fs.exists(root / "a"))
    }

    @Test fun rejectsEscapingIdsAndNames() = runTest {
        source("outside")
        for (id in listOf("../source", "/source", "a/../../source", "a/../x")) {
            assertFailsWith<IOException> { store.get(id) }
            assertFailsWith<IOException> { store.delete(id) }
            assertFailsWith<IOException> { store.download(id, Buffer()) }
            assertFailsWith<IOException> { store.listFiles(listOf(id)) }
            assertFailsWith<IOException> { store.getFolders(id) }
            assertFailsWith<IOException> { store.upload("x", "/source".toPath(), id) }
            assertFailsWith<IOException> { store.createNewFolder("x", id) }
        }
        for (name in listOf("../source", "/source", "a/b", "..", "", ".")) {
            assertFailsWith<IOException> { store.upload(name, "/source".toPath(), "") }
            assertFailsWith<IOException> { store.createNewFolder(name) }
        }
        assertEquals("outside", fs.read("/source".toPath()) { readUtf8() })
    }

    @Test fun rejectsSymlinkTraversalIncludingRecursiveListing() = runTest {
        fs.allowSymlinks = true
        fs.createDirectories("/outside".toPath())
        fs.write("/outside/secret".toPath()) { writeUtf8("secret") }
        fs.createSymlink(root / "link", "/outside".toPath())
        for (id in listOf("link", "link/secret")) {
            assertFailsWith<IOException> { store.get(id) }
            assertFailsWith<IOException> { store.download(id, Buffer()) }
            assertFailsWith<IOException> { store.delete(id) }
            assertFailsWith<IOException> { store.listFiles(listOf(id)) }
            assertFailsWith<IOException> { store.getFolders(id) }
            assertFailsWith<IOException> { store.createNewFolder("new", id) }
            assertFailsWith<IOException> { store.upload("new", source("x"), id) }
        }
        assertFailsWith<IOException> { store.listFiles() }
        assertEquals("secret", fs.read("/outside/secret".toPath()) { readUtf8() })
    }

    @Test fun failedUploadKeepsExistingTargetAndRemovesTemporaryFile() = runTest {
        store.upload("x", source("original"), "")
        assertFailsWith<FileNotFoundException> { store.upload("x", "/absent-source".toPath(), "") }
        assertEquals("original", fs.read(root / "x") { readUtf8() })
        assertEquals(listOf(root / "x"), fs.list(root))
    }

    @Test fun atomicMoveFailurePreservesTargetAndCleansTemporaryFile() = runTest {
        store.upload("x", source("original"), "")
        val failure = IOException("atomic move failed")
        val failingFs = object : okio.ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path): Unit = throw failure
        }
        val failing = FileTreeCloudStore(failingFs, { root }, now = { clock++ })
        assertSame(failure, assertFailsWith<IOException> { failing.upload("x", source("replacement"), "") })
        assertEquals("original", fs.read(root / "x") { readUtf8() })
        assertEquals(listOf(root / "x"), fs.list(root))
    }

    @Test fun cleanupFailureIsSuppressedOnPrimaryUploadFailure() = runTest {
        for (failMove in listOf(false, true)) {
            val primary = IOException(if (failMove) "move failed" else "source failed")
            val cleanup = IOException("cleanup failed")
            val input = source("replacement")
            val failingFs = object : okio.ForwardingFileSystem(fs) {
                override fun source(file: Path): okio.Source {
                    if (!failMove && file == input) throw primary
                    return super.source(file)
                }
                override fun atomicMove(source: Path, target: Path): Unit = throw primary
                override fun delete(path: Path, mustExist: Boolean) {
                    if (".tmp-" in path.name) throw cleanup
                    super.delete(path, mustExist)
                }
            }
            val failing = FileTreeCloudStore(failingFs, { root }, now = { clock++ })
            val thrown = assertFailsWith<IOException> { failing.upload("x", input, "") }
            assertSame(primary, thrown)
            assertEquals(listOf(cleanup), thrown.suppressedExceptions)
            fs.list(root).forEach { fs.delete(it) }
        }
    }

    @Test fun cleanupFailureWithoutPrimaryIsPropagated() = runTest {
        val cleanup = IOException("cleanup failed")
        val failingFs = object : okio.ForwardingFileSystem(fs) {
            override fun delete(path: Path, mustExist: Boolean) {
                if (".tmp-" in path.name) throw cleanup
                super.delete(path, mustExist)
            }
        }
        val failing = FileTreeCloudStore(failingFs, { root }, now = { clock++ })
        assertSame(cleanup, assertFailsWith<IOException> { failing.upload("x", source("content"), "") })
        assertEquals("content", fs.read(root / "x") { readUtf8() })
    }

    @Test fun coordinatorCannotRedirectOutsideRoot() = runTest {
        source("private")
        val coordination = object : FileCoordination {
            override fun <T> read(path: Path, block: (Path) -> T) = block("/source".toPath())
            override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T) = block("/source".toPath())
            override suspend fun ensureDownloaded(path: Path, timeoutMs: Long) = Unit
        }
        val redirected = FileTreeCloudStore(fs, { root }, coordination, now = { clock++ })
        assertFailsWith<IOException> { redirected.download("x", Buffer()) }
        assertFailsWith<IOException> { redirected.upload("x", "/source".toPath(), "") }
        assertEquals("private", fs.read("/source".toPath()) { readUtf8() })
    }

    @Test fun downloadClosesSinkOnSuccessAndMissingFile() = runTest {
        store.upload("x", source("content"), "")
        for (id in listOf("x", "missing")) {
            var closed = false
            val output = object : okio.Sink by Buffer() {
                override fun close() { closed = true }
            }
            if (id == "missing") {
                assertFailsWith<FileNotFoundException> { store.download(id, output) }
            } else store.download(id, output)
            assertTrue(closed)
        }
    }

    @Test fun downloadWaitsForPlaceholderBeforeCheckingExistence() = runTest {
        val coordination = object : FileCoordination {
            override fun <T> read(path: Path, block: (Path) -> T) = block(path)
            override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T) = block(path)
            override suspend fun ensureDownloaded(path: Path, timeoutMs: Long) {
                fs.write(path) { writeUtf8("arrived") }
            }
        }
        val sink = Buffer()
        FileTreeCloudStore(fs, { root }, coordination, now = { clock++ }).download("remote", sink)
        assertEquals("arrived", sink.readUtf8())
    }
}
