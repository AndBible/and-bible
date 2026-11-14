package net.bible.sharedcore.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Keeps the registry and the episode folders under `website/video/episodes/` in step. */
class TutorialVideosEpisodesTest {
    private fun episodesDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, "website/video/episodes").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("website/video/episodes not found above ${System.getProperty("user.dir")}")
    }

    @Test
    fun everyPublishableEpisodeFolderHasARegistryEntryAndViceVersa() {
        val folders = episodesDir().listFiles { f -> f.isDirectory && File(f, "youtube.yaml").isFile }!!
            .map { it.name }
            // The placeholder has its own constant, and the PoC is not part of the series.
            .filter { it != "placeholder-coming-soon" && it != "poc-title-bar-search" }
            .toSortedSet()
        val registry = TutorialVideos.Episode.entries.map { it.slug }.toSortedSet()
        assertEquals(registry, folders)
    }

    @Test
    fun placeholderEpisodeFolderExists() {
        assertNotNull(File(episodesDir(), "placeholder-coming-soon").takeIf { it.isDirectory })
    }
}
