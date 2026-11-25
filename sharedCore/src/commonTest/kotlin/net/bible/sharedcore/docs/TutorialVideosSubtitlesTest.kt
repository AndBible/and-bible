package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TutorialVideosSubtitlesTest {
    private val subs = setOf("fi", "pt", "zh-Hans")
    private val watch = "https://www.youtube.com/watch?v=abc123"
    private val placeholder = "https://www.youtube.com/watch?v=${TutorialVideos.PLACEHOLDER_VIDEO_ID}"

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
        assertEquals("$placeholder&cc_load_policy=1&cc_lang_pref=fi&hl=fi", TutorialVideos.buildUrl(null, emptySet(), "fi"))
        assertEquals("fi", TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, "fi"))
    }

    @Test fun unpublishedEpisodeWithoutMatchingCaptionsKeepsThePlainWatchUrl() {
        assertEquals(placeholder, TutorialVideos.buildUrl(null, emptySet(), "zxx"))
        assertNull(TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, "zxx"))
    }

    @Test fun unpublishedEpisodeInEnglishUsesThePlainPlaceholderUrl() {
        assertEquals(placeholder, TutorialVideos.buildUrl(null, emptySet(), "en-US"))
    }

    @Test fun unpublishedEpisodeIgnoresItsOwnSubtitleMetadata() {
        assertEquals(placeholder, TutorialVideos.buildUrl(null, setOf("zxx"), "zxx"))
    }

    @Test fun placeholderCaptionsMatchPlatformLanguageTags() {
        val cases = mapOf(
            "in" to "id", "id" to "id", "he" to "iw", "iw" to "iw",
            "sr-Latn" to "sr-Latn", "sr-Latn-RS" to "sr-Latn", "sr-Cyrl-RS" to "sr", "sr-RS" to "sr",
            "zh-Hans" to "zh-CN", "zh-Hans-SG" to "zh-CN", "zh-Hans-TW" to "zh-CN", "zh-CN" to "zh-CN", "zh-SG" to "zh-CN",
            "zh" to "zh-TW", "zh-MO" to "zh-TW",
            "zh-Hant" to "zh-TW", "zh-Hant-HK" to "zh-TW", "zh-Hant-CN" to "zh-TW", "zh-TW" to "zh-TW", "zh-HK" to "zh-TW",
            "nb" to "nb", "no" to "nb", "no-NO" to "nb", "tl" to "fil", "fil" to "fil",
        )
        for ((app, caption) in cases) {
            assertEquals(caption, TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, app), app)
            assertEquals(
                "$placeholder&cc_load_policy=1&cc_lang_pref=$caption&hl=$caption",
                TutorialVideos.buildUrl(null, emptySet(), app), app,
            )
        }
    }

    @Test fun bareChineseAndMacaoUseTheAppsTraditionalChineseCaptions() {
        for (tag in listOf("zh", "zh-MO")) {
            assertEquals("zh-TW", TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, tag), tag)
        }
    }

    @Test fun cantoneseDoesNotSelectAMandarinTrack() {
        assertNull(TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, "yue"))
        assertNull(TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, "yue-Hant-HK"))
    }

    @Test fun explicitSimplifiedChineseInMacaoDoesNotSelectTraditionalCaptions() {
        assertNull(TutorialVideos.matchSubtitle(setOf("zh-TW"), "zh-Hans-MO"))
        assertEquals("zh-CN", TutorialVideos.matchSubtitle(setOf("zh-CN", "zh-TW"), "zh-Hans-MO"))
    }

    @Test fun tagIsUrlEncoded() {
        val url = TutorialVideos.buildUrl("abc123", setOf("x&y=z"), "x&y=z")
        assertTrue("cc_lang_pref=x%26y%3Dz&hl=x%26y%3Dz" in url, url)
    }

    @Test fun englishAppKeepsTheUnpublishedPlaceholderUrl() {
        assertEquals(placeholder, TutorialVideos.buildUrl(null, emptySet(), "en"))
        assertNull(TutorialVideos.matchSubtitle(TutorialVideos.PLACEHOLDER_SUBTITLES, "en"))
    }
}
