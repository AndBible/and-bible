package net.bible.service.sword.epub

import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubStagingSweepTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "epub-staging-sweep-${kotlin.random.Random.nextLong().toULong()}"

    private var previousHost: DatabaseHost? = null
    private lateinit var dbDir: String

    /** The sweep drops app databases through the global [DatabaseContainer.host]; put back what was installed. */
    @BeforeTest fun installHost() {
        previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        dbDir = newTestDir()
        DatabaseContainer.installHost(FakeDatabaseHost(dbDir))
    }

    @AfterTest fun cleanUp() {
        previousHost?.let { DatabaseContainer.installHost(it) }
        fs.deleteRecursively(dbDir.toPath(), mustExist = false)
        fs.deleteRecursively(root, mustExist = false)
    }

    private fun write(file: Path, text: String) {
        fs.createDirectories(file.parent!!)
        fs.write(file) { writeUtf8(text) }
    }
    private fun read(file: Path) = fs.read(file) { readUtf8() }
    private fun leftovers() = fs.list(root).filter { it.name.startsWith(".epub-") }

    /** #3005 review: an archive install's sweep must not delete the staging tree of a concurrent direct install. */
    @Test fun sweepKeepsActiveStagingAndRemovesCrashLeftovers() {
        val active = newEpubStagingDir(root)
        fs.createDirectories(active / "book.epub")
        val leftover = root / "${EPUB_STAGING_PREFIX}crashed"
        fs.createDirectories(leftover / "book.epub")
        val unrelated = root / "epub"
        fs.createDirectories(unrelated)
        try {
            sweepStaleEpubStagingDirs(root)
            assertTrue(fs.exists(active), "active staging must survive the sweep")
            assertFalse(fs.exists(leftover), "a crashed install's staging is removed")
            assertTrue(fs.exists(unrelated))
        } finally {
            releaseEpubStagingDir(active)
        }
        assertFalse(fs.exists(active), "release deletes the staging tree")
        fs.createDirectories(active)
        sweepStaleEpubStagingDirs(root)
        assertFalse(fs.exists(active), "a released staging path is no longer protected")
    }

    /**
     * #3005 review: the archive install roots its staging at the SWORD download dir and the direct install at the
     * modules dir; the same folder spelled differently must still be protected.
     */
    @Test fun sweepKeepsActiveStagingCreatedThroughAnotherSpellingOfTheRoot() {
        fs.createDirectories(root / "sub")
        val otherSpelling = "$root/sub/..".toPath()
        val active = newEpubStagingDir(otherSpelling)
        fs.createDirectories(active / "book.epub")
        val aside = newEpubAsideDir(otherSpelling)
        write(aside / "book.epub" / "content.opf", "old")
        try {
            sweepStaleEpubStagingDirs(root)
            assertTrue(fs.exists(root / active.name / "book.epub"), "active staging must survive the sweep")
            assertEquals("old", read(root / aside.name / "book.epub" / "content.opf"), "an active aside is not touched")
            assertFalse(fs.exists(root / "epub" / "book.epub"), "an active aside is not restored")
        } finally {
            releaseEpubStagingDir(active)
            releaseEpubStagingDir(aside)
        }
    }

    /** #3005 review: an app killed between "move old aside" and "move new in" left the module only in the aside tree. */
    @Test fun sweepRestoresACrashedAsideWhenItsModuleIsMissing() {
        write(root / "${EPUB_ASIDE_PREFIX}crashed" / "book.epub" / "content.opf", "old")
        write(root / "${EPUB_STAGING_PREFIX}crashed" / "book.epub" / "content.opf", "new")

        sweepStaleEpubStagingDirs(root)

        assertEquals("old", read(root / "epub" / "book.epub" / "content.opf"), "the old module is back in place")
        assertEquals(emptyList(), leftovers())
    }

    /** A storage error during a sweep must not remove the old module it could not restore. */
    @Test fun sweepKeepsAnAsideWhenItsRestoreFails() {
        val aside = root / "${EPUB_ASIDE_PREFIX}crashed"
        write(aside / "book.epub" / "content.opf", "old")
        write(root / "epub", "not a directory")

        sweepStaleEpubStagingDirs(root)

        assertEquals("old", read(aside / "book.epub" / "content.opf"))
        fs.delete(root / "epub")
        sweepStaleEpubStagingDirs(root)
        assertEquals("old", read(root / "epub" / "book.epub" / "content.opf"))
        assertEquals(emptyList(), leftovers())
    }

    /** A crash after moving the new tree in is not proof that its registration succeeded. */
    @Test fun sweepRestoresAnUncommittedAsideEvenWhenItsModuleIsPresent() {
        write(root / "${EPUB_ASIDE_PREFIX}crashed" / "book.epub" / "content.opf", "old")
        write(root / "epub" / "book.epub" / "content.opf", "new")

        sweepStaleEpubStagingDirs(root)

        assertEquals("old", read(root / "epub" / "book.epub" / "content.opf"))
        assertEquals(emptyList(), leftovers())
    }

    /** Once registration committed, a crash during cleanup must not undo the successful update. */
    @Test fun sweepDropsACommittedOldTree() {
        // Successful publication moves the obsolete aside into a staging-named trash tree atomically.
        val trash = root / "${EPUB_STAGING_PREFIX}committed"
        write(trash / "book.epub" / "content.opf", "old")
        write(root / "epub" / "book.epub" / "content.opf", "new")

        sweepStaleEpubStagingDirs(root)

        assertEquals("new", read(root / "epub" / "book.epub" / "content.opf"))
        assertEquals(emptyList(), leftovers())
    }

    /**
     * #3914 review: a crash after the new module registered but before the aside was dropped leaves the new module's app
     * database behind. The restored old module would reuse it, so the sweep drops it first.
     */
    @Test fun sweepDropsTheNewModulesAppDatabaseBeforeRestoringTheAside() {
        val appDb = "$dbDir/epub-${epubInitials("book.epub")}.sqlite3".toPath()
        val unrelatedDb = "$dbDir/epub-${epubInitials("other.epub")}.sqlite3".toPath()
        for (db in listOf(appDb, unrelatedDb)) fs.write(db) { writeUtf8("new module data") }
        fs.write("$appDb-wal".toPath()) { writeUtf8("wal") }
        write(root / "${EPUB_ASIDE_PREFIX}crashed" / "book.epub" / "content.opf", "old")
        write(root / "epub" / "book.epub" / "content.opf", "new")

        sweepStaleEpubStagingDirs(root)

        assertEquals("old", read(root / "epub" / "book.epub" / "content.opf"))
        assertFalse(fs.exists(appDb), "the new module's app database must not be reused by the restored module")
        assertFalse(fs.exists("$appDb-wal".toPath()))
        assertTrue(fs.exists(unrelatedDb), "other modules' databases are untouched")
    }

    /** #3005 review: a rollback whose delete of the new files failed must still put the old module back. */
    @Test fun restoreAsideOntoANonEmptyModuleFolder() {
        val dir = root / "epub" / "book.epub"
        val aside = newEpubAsideDir(root)
        write(aside / dir.name / "content.opf", "old")
        write(dir / "content.opf", "new")
        write(dir / "OEBPS" / "ch1.xhtml", "new chapter")

        restoreEpubAside(dir, aside, root)

        assertEquals("old", read(dir / "content.opf"))
        assertFalse(fs.exists(dir / "OEBPS"), "no new file is left in the restored module")
        releaseEpubStagingDir(aside)
        assertEquals(emptyList(), leftovers())
    }
}
