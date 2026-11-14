package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorialVideosTest {
    private val channel = "https://www.youtube.com/@AndBible"

    @Test
    fun episodeWithItsOwnIdWinsOverThePlaceholder() =
        assertEquals("https://www.youtube.com/watch?v=ep", TutorialVideos.resolve("ep", "ph"))

    @Test
    fun episodeWithoutIdFallsBackToThePlaceholderVideo() =
        assertEquals("https://www.youtube.com/watch?v=ph", TutorialVideos.resolve(null, "ph"))

    @Test
    fun withNeitherIdTheChannelIsUsed() = assertEquals(channel, TutorialVideos.resolve(null, null))

    @Test
    fun playlistFallsBackThroughThePlaceholderToTheChannel() {
        assertEquals("https://www.youtube.com/playlist?list=PL1", TutorialVideos.resolvePlaylist("PL1", "ph"))
        assertEquals("https://www.youtube.com/watch?v=ph", TutorialVideos.resolvePlaylist(null, "ph"))
        assertEquals(channel, TutorialVideos.resolvePlaylist(null, null))
    }

    @Test
    fun everyEpisodeResolvesToAnHttpsYoutubeUrl() {
        for (episode in TutorialVideos.Episode.entries) {
            assertTrue(TutorialVideos.url(episode).startsWith("https://www.youtube.com/"), episode.name)
        }
    }

    @Test
    fun slugIsTheEpisodeFolderName() {
        assertEquals("whats-new-6-0", TutorialVideos.Episode.WHATS_NEW_6_0.slug)
        assertEquals("tutorial-14-speak", TutorialVideos.Episode.TUTORIAL_14_SPEAK.slug)
        assertEquals(33, TutorialVideos.Episode.entries.size)
    }
}
