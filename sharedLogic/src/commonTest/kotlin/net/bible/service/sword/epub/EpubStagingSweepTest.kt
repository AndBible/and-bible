package net.bible.service.sword.epub

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubStagingSweepTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "epub-staging-sweep-${kotlin.random.Random.nextLong().toULong()}"

    @AfterTest fun cleanUp() = fs.deleteRecursively(root, mustExist = false)

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

    /** A crash after the new module was moved in: the aside is stale and only removed. */
    @Test fun sweepDropsACrashedAsideWhenItsModuleIsPresent() {
        write(root / "${EPUB_ASIDE_PREFIX}crashed" / "book.epub" / "content.opf", "old")
        write(root / "epub" / "book.epub" / "content.opf", "new")

        sweepStaleEpubStagingDirs(root)

        assertEquals("new", read(root / "epub" / "book.epub" / "content.opf"))
        assertEquals(emptyList(), leftovers())
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
