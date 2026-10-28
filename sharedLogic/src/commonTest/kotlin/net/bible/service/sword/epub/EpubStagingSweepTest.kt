package net.bible.service.sword.epub

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubStagingSweepTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "epub-staging-sweep-${kotlin.random.Random.nextLong().toULong()}"

    @AfterTest fun cleanUp() = fs.deleteRecursively(root, mustExist = false)

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
}
