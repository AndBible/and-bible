package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TutorialVideosSubtitlesTest {
    private val subs = setOf("fi", "pt", "zh-Hans")
    private val watch = "https://www.youtube.com/watch?v=abc123"

    @Test fun exactMatchWinsAndIsCaseInsensitive() {
        assertEquals("zh-Hans", TutorialVideos.matchSubtitle(subs, "zh_hans"))
        assertEquals("fi", TutorialVideos.matchSubtitle(subs, "FI"))
    }

    @Test fun primarySubtagOfTheAppTagIsTheFallback() {
        assertEquals("pt", TutorialVideos.matchSubtitle(subs, "pt-BR"))
        assertEquals("pt", TutorialVideos.matchSubtitle(setOf("pt", "pt-BR"), "pt-PT"))
        assertEquals("pt-BR", TutorialVideos.matchSubtitle(setOf("pt", "pt-BR"), "pt-BR"))
    }

    @Test fun regionalSubtitleDoesNotMatchBareAppLanguage() {
        assertNull(TutorialVideos.matchSubtitle(setOf("pt-BR"), "pt"))
        assertNull(TutorialVideos.matchSubtitle(subs, "de"))
    }

    @Test fun scriptAndRegionFallbacks() {
        assertEquals("zh-TW", TutorialVideos.matchSubtitle(setOf("zh-TW", "zh-CN"), "zh-Hant-TW"))
        assertEquals("zh-Hant", TutorialVideos.matchSubtitle(setOf("zh-Hant", "zh-TW"), "zh-Hant-TW"))
        assertEquals("zh-CN", TutorialVideos.matchSubtitle(setOf("zh-TW", "zh-CN"), "zh-Hans-CN"))
        assertEquals("zh-Hans", TutorialVideos.matchSubtitle(setOf("zh-Hans"), "zh-Hans-CN"))
        assertEquals("sr-Latn", TutorialVideos.matchSubtitle(setOf("sr", "sr-Latn"), "sr-Latn-RS"))
        assertEquals("zh", TutorialVideos.matchSubtitle(setOf("zh"), "zh-Hant-TW"))
        assertNull(TutorialVideos.matchSubtitle(setOf("zh-CN"), "zh-Hant-TW"))
    }

    @Test fun legacyLanguageAliasesMatchBothWays() {
        assertEquals("he", TutorialVideos.matchSubtitle(setOf("he"), "iw"))
        assertEquals("iw", TutorialVideos.matchSubtitle(setOf("iw"), "he-IL"))
        assertEquals("id", TutorialVideos.matchSubtitle(setOf("id"), "in"))
        assertEquals("in", TutorialVideos.matchSubtitle(setOf("in"), "id"))
        assertEquals("yi", TutorialVideos.matchSubtitle(setOf("yi"), "ji"))
        assertEquals("ji", TutorialVideos.matchSubtitle(setOf("ji"), "yi"))
    }

    @Test fun englishNeverMatches() {
        assertNull(TutorialVideos.matchSubtitle(setOf("en", "en-GB"), "en"))
        assertNull(TutorialVideos.matchSubtitle(setOf("en-GB"), "en-GB"))
        assertEquals(watch, TutorialVideos.buildUrl("abc123", setOf("en"), "en-US"))
    }

    @Test fun publishedEpisodeWithMatchAsksForCaptionsAndInterfaceLanguage() {
        assertEquals("$watch&cc_load_policy=1&cc_lang_pref=pt&hl=pt-BR", TutorialVideos.buildUrl("abc123", subs, "pt_BR"))
    }

    @Test fun publishedEpisodeWithoutMatchIsThePlainWatchUrl() {
        assertEquals(watch, TutorialVideos.buildUrl("abc123", subs, "de"))
    }

    @Test fun unpublishedEpisodeGivesThePlaceholderWithoutCaptionParameters() {
        val url = TutorialVideos.buildUrl(null, subs, "fi")
        assertEquals(TutorialVideos.resolve(null, TutorialVideos.PLACEHOLDER_VIDEO_ID), url)
        assertFalse("cc_" in url)
    }

    @Test fun tagIsUrlEncoded() {
        val url = TutorialVideos.buildUrl("abc123", setOf("x&y=z"), "x&y=z")
        assertTrue("cc_lang_pref=x%26y%3Dz&hl=x%26y%3Dz" in url, url)
    }

    @Test fun labelIsPlainForEnglishOrMatchedCaptions() {
        assertTrue(TutorialVideos.plainLabel(emptySet(), "en-GB"))
        assertTrue(TutorialVideos.plainLabel(subs, "fi"))
        assertFalse(TutorialVideos.plainLabel(subs, "de"))
        assertFalse(TutorialVideos.plainLabel(emptySet(), "fi"))
    }

    @Test fun englishAppKeepsTheEpisodesOwnUrl() {
        val e = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        assertEquals(TutorialVideos.url(e), TutorialVideos.url(e, "en"))
        assertNull(TutorialVideos.subtitleLanguage(e, "en"))
    }
}
