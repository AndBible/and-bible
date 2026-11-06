package net.bible.service.sword.epub

import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.FakeDatabaseHost
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A repo-installed EPUB whose state cannot be built (corrupt package) is listed, but must stay harmless and removable. */
class EpubBrokenModuleTest {
    private fun metadata(initials: String) =
        SwordBookMetaData("[$initials]\nModDrv=EpubBook\nDescription=Broken\n".encodeToByteArray(), "$initials.conf")

    private class Counting {
        var builds = 0
        val failure = IllegalStateException("corrupt epub")
        val create: () -> EpubBackendState = { builds++; throw failure }
    }

    @Test fun failedBuildIsCachedAndReadersTolerateIt() {
        val c = Counting()
        val backend = EpubBackend(c.create, metadata("Epub-broken"))
        assertEquals(0, c.builds, "listing must not build the state")
        val first = runCatching { backend.state }.exceptionOrNull()
        val second = runCatching { backend.state }.exceptionOrNull()
        assertSame(c.failure, first)
        assertSame(c.failure, second)
        assertNull(backend.stateOrNull)
        assertFalse(backend.isIndexed)
        assertNull(backend.openedStateOrNull)
        backend.closeIfOpened() // nothing to close, must not throw
        assertEquals(1, c.builds, "the failed build is attempted exactly once")
    }

    @Test fun closeIfOpenedNeverBuildsTheState() {
        val c = Counting()
        EpubBackend(c.create, metadata("Epub-unopened")).closeIfOpened()
        assertEquals(0, c.builds)
    }

    @Test fun retiringAnUnopenedOrBrokenModuleDoesNotBuildItsState() {
        val root = Files.createTempDirectory("epub-retire")
        val previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        val dir = root.resolve("brokenretire")
        Files.createDirectories(dir)
        val initials = epubInitials("brokenretire")
        val c = Counting()
        val book = SwordGenBook(metadata(initials), EpubBackend(c.create, metadata(initials)))
        try {
            DatabaseContainer.installHost(FakeDatabaseHost(root.toString()))
            Books.installed().addBook(book)
            assertNotNull(Books.installed().getBook(initials))
            retireOwnedEpubModule(dir.toString().toPath())
            assertNull(Books.installed().getBook(initials), "the broken module is unregistered")
            assertEquals(0, c.builds, "retiring must not build the state")
        } finally {
            Books.installed().getBook(initials)?.let { Books.installed().removeBook(it) }
            previousHost?.let { DatabaseContainer.installHost(it) }
            root.toFile().deleteRecursively()
        }
    }
}
