package net.bible.sharedcore.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Keeps the registry and the episode folders under `website/video/episodes/` in step. */
class TutorialVideosEpisodesTest {
    /**
     * `website/video` is a private submodule (`update = none`): CI and contributors do not check it out, so these
     * checks run only where it is present. The public side is covered by website/tests/test_tutorials.py
     * (tutorials.yaml against this registry).
     */
    private fun episodesDir(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, "website/video/episodes").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun everyPublishableEpisodeFolderHasARegistryEntryAndViceVersa() {
        val episodes = episodesDir() ?: return
        val folders = episodes.listFiles { f -> f.isDirectory && File(f, "youtube.yaml").isFile }!!
            .map { it.name }
            // The placeholder has its own constant; the PoC and the 6.0 teaser are not part of the series.
            .filter { it != "placeholder-coming-soon" && it != "poc-title-bar-search" && it != "teaser-6-0" }
            .toSortedSet()
        val registry = TutorialVideos.Episode.entries.map { it.slug }.toSortedSet()
        assertEquals(registry, folders)
    }

    @Test
    fun placeholderEpisodeFolderExists() {
        val episodes = episodesDir() ?: return
        assertNotNull(File(episodes, "placeholder-coming-soon").takeIf { it.isDirectory })
    }
}
