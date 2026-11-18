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

    @Test fun explicitChineseScriptDoesNotFallBackToAnIncompatibleRegionalTrack() {
        assertNull(TutorialVideos.matchSubtitle(setOf("zh-TW"), "zh-Hans-TW"))
        assertNull(TutorialVideos.matchSubtitle(setOf("zh-CN"), "zh-Hant-CN"))
        assertEquals("zh", TutorialVideos.matchSubtitle(setOf("zh", "zh-TW"), "zh-Hans-TW"))
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
        assertEquals("$watch&cc_load_policy=1&cc_lang_pref=pt&hl=pt", TutorialVideos.buildUrl("abc123", subs, "pt_BR"))
    }

    @Test fun publishedEpisodeWithoutMatchIsThePlainWatchUrl() {
        assertEquals(watch, TutorialVideos.buildUrl("abc123", subs, "de"))
    }

    @Test fun unpublishedEpisodeRequestsThePlaceholdersCaptions() {
        val episode = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        for (language in listOf("fi", "de", "sv")) {
            assertEquals(
                "https://www.youtube.com/watch?v=RlLkpKZZwTg&cc_load_policy=1&cc_lang_pref=$language&hl=$language",
                TutorialVideos.url(episode, language),
            )
            assertTrue(TutorialVideos.hasPlainLabel(episode, language), language)
            assertEquals(language, TutorialVideos.subtitleLanguage(episode, language))
        }
    }

    @Test fun unpublishedEpisodeWithoutMatchingCaptionsKeepsTheEnglishQualifier() {
        val episode = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        assertEquals("https://www.youtube.com/watch?v=RlLkpKZZwTg", TutorialVideos.url(episode, "zxx"))
        assertFalse(TutorialVideos.hasPlainLabel(episode, "zxx"))
        assertNull(TutorialVideos.subtitleLanguage(episode, "zxx"))
    }

    @Test fun unpublishedEpisodeInEnglishUsesThePlainPlaceholderUrlAndLabel() {
        val episode = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        assertEquals("https://www.youtube.com/watch?v=RlLkpKZZwTg", TutorialVideos.url(episode, "en-US"))
        assertTrue(TutorialVideos.hasPlainLabel(episode, "en-US"))
    }

    @Test fun unpublishedEpisodeIgnoresItsOwnSubtitleMetadata() {
        assertEquals("https://www.youtube.com/watch?v=RlLkpKZZwTg", TutorialVideos.buildUrl(null, setOf("zxx"), "zxx"))
    }

    @Test fun placeholderCaptionsMatchPlatformLanguageTags() {
        val episode = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        val cases = mapOf(
            "in" to "id", "id" to "id", "he" to "iw", "iw" to "iw",
            "sr-Latn" to "sr-Latn", "sr-Latn-RS" to "sr-Latn", "sr-Cyrl-RS" to "sr", "sr-RS" to "sr",
            "zh-Hans" to "zh-CN", "zh-Hans-SG" to "zh-CN", "zh-Hans-TW" to "zh-CN", "zh-CN" to "zh-CN", "zh-SG" to "zh-CN",
            "zh" to "zh-TW", "zh-MO" to "zh-TW",
            "zh-Hant" to "zh-TW", "zh-Hant-HK" to "zh-TW", "zh-Hant-CN" to "zh-TW", "zh-TW" to "zh-TW", "zh-HK" to "zh-TW",
            "nb" to "nb", "no" to "nb", "no-NO" to "nb", "tl" to "fil", "fil" to "fil",
        )
        for ((app, caption) in cases) {
            assertEquals(caption, TutorialVideos.subtitleLanguage(episode, app), app)
            assertEquals(
                "https://www.youtube.com/watch?v=RlLkpKZZwTg&cc_load_policy=1&cc_lang_pref=$caption&hl=$caption",
                TutorialVideos.url(episode, app), app,
            )
            assertTrue(TutorialVideos.hasPlainLabel(episode, app), app)
        }
    }

    @Test fun bareChineseAndMacaoUseTheAppsTraditionalChineseCaptions() {
        val episode = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        for (tag in listOf("zh", "zh-MO")) {
            assertEquals("zh-TW", TutorialVideos.subtitleLanguage(episode, tag), tag)
        }
    }

    @Test fun cantoneseDoesNotSelectAMandarinTrack() {
        val episode = TutorialVideos.Episode.TUTORIAL_08_BOOKMARKS
        assertNull(TutorialVideos.subtitleLanguage(episode, "yue"))
        assertNull(TutorialVideos.subtitleLanguage(episode, "yue-Hant-HK"))
        assertFalse(TutorialVideos.hasPlainLabel(episode, "yue"))
    }

    @Test fun explicitSimplifiedChineseInMacaoDoesNotSelectTraditionalCaptions() {
        assertNull(TutorialVideos.matchSubtitle(setOf("zh-TW"), "zh-Hans-MO"))
        assertEquals("zh-CN", TutorialVideos.matchSubtitle(setOf("zh-CN", "zh-TW"), "zh-Hans-MO"))
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
        assertEquals("https://www.youtube.com/watch?v=RlLkpKZZwTg", TutorialVideos.url(e, "en"))
        assertNull(TutorialVideos.subtitleLanguage(e, "en"))
    }
}
