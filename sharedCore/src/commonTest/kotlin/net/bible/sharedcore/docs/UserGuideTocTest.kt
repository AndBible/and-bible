package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserGuideTocTest {
    private val pages = UserGuideToc.sections.flatMap { it.pages }

    @Test
    fun everyPageUrlIsADocsUrl() = assertTrue(pages.all { it.url.startsWith(DocsLinks.BASE_URL) })

    @Test
    fun stemsAreUniqueAndSectionsNonEmpty() {
        assertEquals(pages.size, pages.map { it.stem }.toSet().size)
        assertTrue(UserGuideToc.sections.isNotEmpty())
        assertTrue(UserGuideToc.sections.all { it.pages.isNotEmpty() && it.title.isNotBlank() })
        assertTrue(pages.all { it.title.isNotBlank() })
    }

    @Test
    fun publishedEpisodeFilterOnlyIncludesPublishedEpisodes() {
        val published = TutorialVideos.published().toSet()
        assertTrue(pages.all { page -> page.publishedEpisodes.all { it in published } })
        assertEquals(published.filter { e -> pages.any { e in it.episodes } }.toSet(), pages.flatMap { it.publishedEpisodes }.toSet())
    }

    @Test
    fun everyEpisodeHasATitle() {
        assertEquals(TutorialVideos.Episode.entries.toSet(), UserGuideToc.episodeTitles.keys)
        assertTrue(UserGuideToc.episodeTitles.values.all { it.isNotBlank() })
        assertEquals("What's new in AndBible 6.0", UserGuideToc.episodeTitles[TutorialVideos.Episode.WHATS_NEW_6_0])
    }
}
