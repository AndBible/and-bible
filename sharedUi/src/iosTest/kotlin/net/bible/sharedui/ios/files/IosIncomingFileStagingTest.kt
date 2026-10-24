@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.bible.sharedui.ios.files

import kotlinx.coroutines.runBlocking
import net.bible.sharedui.app.ports.DocumentRef
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IosIncomingFileStagingTest {
    @Test fun copiesExactExternalUrlRatherThanNormalizedBackslashNeighbour() = withFolder { root ->
        val external = root / "external"
        FileSystem.SYSTEM.createDirectories(external / "part")
        val exact = NSURL.fileURLWithPath("$external/part\\book # % å.epub")
        val neighbour = NSURL.fileURLWithPath("$external/part/book # % å.epub")
        write(exact, "the selected book")
        write(neighbour, "wrong normalized neighbour")
        val staging = staging(root)
        val copied = staging.prepare(exact.absoluteString!!)()
        assertEquals("the selected book", read(copied))
        assertNotEquals(exact.absoluteString, copied.uri)
        assertTrue(copied.uri.startsWith("file:///"))
        assertEquals("epub", NSURL.URLWithString(copied.uri)!!.pathExtension)
    }

    @Test fun accessStartsBeforeDeferredCopyAndStopsOnlyAfterOwnedCopyExists() = withFolder { root ->
        val source = NSURL.fileURLWithPath("$root/source.zip")
        write(source, "source data")
        var starts = 0
        var stops = 0
        val staging = staging(root, start = {
            assertEquals(source.absoluteString, it.absoluteString)
            starts++
            true
        }, stop = {
            assertEquals(source.absoluteString, it.absoluteString)
            stops++
            // An actual owned file must already exist before releasing external access.
            val namespace = root / "incoming"
            assertTrue(FileSystem.SYSTEM.listRecursively(namespace).any { FileSystem.SYSTEM.metadata(it).isRegularFile })
        })
        val pending = staging.prepare(source.absoluteString!!)
        assertEquals(1, starts)
        assertEquals(0, stops)
        val copied = pending()
        assertEquals(1, stops)
        assertTrue(NSFileManager.defaultManager.removeItemAtURL(source, null))
        assertEquals("source data", read(copied), "The queue no longer depends on the external provider")
    }

    @Test fun failedCopyReleasesAcquiredScopeAndDoesNotPreventNextCopy() = withFolder { root ->
        var starts = 0
        var stops = 0
        val staging = staging(root, start = { starts++; true }, stop = { stops++ })
        val missing = NSURL.fileURLWithPath("$root/missing.zip")
        assertFails { staging.prepare(missing.absoluteString!!)() }
        assertEquals(1, starts)
        assertEquals(1, stops)
        val source = NSURL.fileURLWithPath("$root/good.zip")
        write(source, "good")
        assertEquals("good", read(staging.prepare(source.absoluteString!!)()))
        assertEquals(2, starts)
        assertEquals(2, stops)
    }

    @Test fun sandboxFileStillCopiesWhenSecurityScopeIsUnavailable() = withFolder { root ->
        val source = NSURL.fileURLWithPath("$root/local.ttf")
        write(source, "font bytes")
        val staging = staging(root, start = { false }, stop = { error("No acquired scope to stop") })
        assertEquals("font bytes", read(staging.prepare(source.absoluteString!!)()))
    }

    @Test fun sameNameAndRepeatedSourceHaveIndependentOwnedCopies() = withFolder { root ->
        FileSystem.SYSTEM.createDirectories(root / "one")
        FileSystem.SYSTEM.createDirectories(root / "two")
        val one = NSURL.fileURLWithPath("$root/one/book.zip")
        val two = NSURL.fileURLWithPath("$root/two/book.zip")
        write(one, "first")
        write(two, "second")
        val staging = staging(root)
        val first = staging.prepare(one.absoluteString!!)()
        val second = staging.prepare(two.absoluteString!!)()
        val repeated = staging.prepare(one.absoluteString!!)()
        assertEquals(3, setOf(first.uri, second.uri, repeated.uri).size)
        assertEquals(listOf("first", "second", "first"), listOf(first, second, repeated).map(::read))
    }

    @Test fun nextProcessCleansOnlyOwnOldNamespaceAndRetainsItsCurrentFiles() = withFolder { root ->
        val namespace = root / "incoming"
        val unrelated = root / "other-cache"
        FileSystem.SYSTEM.createDirectories(unrelated)
        FileSystem.SYSTEM.write(unrelated / "keep") { writeUtf8("unrelated") }
        val source = NSURL.fileURLWithPath("$root/book.zip")
        write(source, "first process")
        val firstProcess = staging(root, processId = "first-process")
        val old = firstProcess.prepare(source.absoluteString!!)()
        val secondProcess = staging(root, processId = "second-process")
        val current = secondProcess.prepare(source.absoluteString!!)()
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(NSURL.URLWithString(old.uri)!!.path!!))
        assertEquals("first process", read(current))
        write(source, "later input")
        val later = secondProcess.prepare(source.absoluteString!!)()
        assertEquals("first process", read(current), "Normal offers must not clean this process's staged inputs")
        assertEquals("later input", read(later))
        assertEquals("unrelated", FileSystem.SYSTEM.read(unrelated / "keep") { readUtf8() })
        assertTrue(FileSystem.SYSTEM.exists(namespace / "second-process"))
    }

    private fun staging(
        root: Path,
        processId: String = "current-process",
        start: (NSURL) -> Boolean = { false },
        stop: (NSURL) -> Unit = {},
    ) = IosIncomingFileStaging(
        namespace = NSURL.fileURLWithPath((root / "incoming").toString()),
        processId = processId,
        startAccess = start,
        stopAccess = stop,
    )

    private fun write(url: NSURL, content: String) {
        assertTrue(NSString.create(string = content).writeToURL(url, true, NSUTF8StringEncoding, null))
    }

    private fun read(ref: DocumentRef): String? =
        NSString.stringWithContentsOfURL(NSURL.URLWithString(ref.uri)!!, NSUTF8StringEncoding, null)

    private fun withFolder(block: suspend (Path) -> Unit) = runBlocking {
        val root = (NSTemporaryDirectory() + "incoming-test-" + NSUUID().UUIDString).toPath()
        FileSystem.SYSTEM.createDirectories(root)
        try { block(root) } finally {
            // Foundation cleanup also handles the literal POSIX backslash fixture.
            assertTrue(NSFileManager.defaultManager.removeItemAtURL(NSURL.fileURLWithPath(root.toString()), null))
        }
    }
}
