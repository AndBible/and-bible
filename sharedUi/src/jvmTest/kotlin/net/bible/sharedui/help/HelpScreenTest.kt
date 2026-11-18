package net.bible.sharedui.help

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.docs.TutorialVideos.Episode
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HelpScreenTest {
    private val strings = iosStrings("en")

    private val withVideo = UserGuideToc.Page("navigation", "Navigation page", "https://x/navigation/", listOf(Episode.TUTORIAL_02_FINDING_YOUR_WAY))
    private val noVideo = UserGuideToc.Page("faq", "Faq page", "https://x/faq/", listOf(Episode.TUTORIAL_03_VERSE_MENU))
    private val sections = listOf(UserGuideToc.Section("Section One", listOf(withVideo, noVideo)))
    private val tips = listOf(
        HelpTipUi("Tip title", "line one\nline two", "https://video/1", "Watch it", "https://docs/1"),
        HelpTipUi("Plain tip", "plain", null, "Watch it", null),
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        videos: List<VideoRowUi> = listOf(VideoRowUi("Episode one", "https://video/e1")),
        opened: MutableList<String> = mutableListOf(),
        onUp: () -> Unit = {},
        initialTab: Int = 0,
        appLanguage: String = "en",
        guideSections: List<UserGuideToc.Section> = sections,
    ) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                HelpScreen(
                    tips = tips, sections = guideSections, tutorialVideos = videos, versionText = "Version 9.9",
                    appLanguage = appLanguage, initialTab = initialTab, onOpenUrl = { opened += it }, onUp = onUp,
                )
            }
        }
    }

    @Test fun tipsShownOnFirstTab() = runComposeUiTest {
        show()
        onNodeWithText("Tip title").assertExists()
        onNodeWithText("Plain tip").assertExists()
        onNodeWithText("Version 9.9").assertExists()
        onNodeWithText("Section One").assertDoesNotExist()
    }

    @Test fun tipLinksOpenTheirUrls() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened)
        onNodeWithText(strings.helpReadMoreLink).performClick()
        assertEquals(listOf("https://docs/1"), opened)
    }

    @Test fun tipsTabHasTheSponsorLink() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened)
        onNodeWithText("${strings.buyDevelopment2}: ${strings.buyDevelopment}").performClick()
        assertEquals(listOf(net.bible.sharedcore.docs.SiteLinks.SHOP_URL), opened)
    }

    @Test fun userGuideTabShowsSectionsAndOpensPages() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened)
        onNodeWithText(strings.helpTabUserGuide).performClick()
        onNodeWithText("Section One").assertExists()
        onNodeWithText("Tip title").assertDoesNotExist()
        onNodeWithText("Navigation page").performClick()
        assertEquals(listOf("https://x/navigation/"), opened)
    }

    @Test fun unpublishedEpisodesGetPlayableRowsUnderTheirPages() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened)
        onNodeWithText(strings.helpTabUserGuide).performClick()
        val title = UserGuideToc.episodeTitles.getValue(Episode.TUTORIAL_02_FINDING_YOUR_WAY)
        onAllNodesWithText(title).assertCountEquals(1)
        onNodeWithText(UserGuideToc.episodeTitles.getValue(Episode.TUTORIAL_03_VERSE_MENU)).assertExists()
        onNodeWithText(title).performClick()
        assertEquals(listOf("https://www.youtube.com/watch?v=RlLkpKZZwTg"), opened)
    }

    @Test fun episodeRowsUseTheAppLanguageForUrlAndLabel() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val episode = Episode.TUTORIAL_02_FINDING_YOUR_WAY
        show(videos = emptyList(), opened = opened, appLanguage = "fi")
        onNodeWithText(strings.helpTabUserGuide).performClick()
        onNodeWithText(UserGuideToc.episodeTitles.getValue(episode)).assertExists()
        onAllNodesWithText(strings.watchTutorialVideoPlain).assertCountEquals(2)
        onNodeWithText(UserGuideToc.episodeTitles.getValue(episode)).performClick()
        assertEquals(listOf("https://www.youtube.com/watch?v=RlLkpKZZwTg&cc_load_policy=1&cc_lang_pref=fi&hl=fi"), opened)
    }

    @Test fun videoRowWithoutCaptionsCarriesTheEnglishQualifier() = runComposeUiTest {
        show(videos = listOf(VideoRowUi("Episode one", "https://video/e1", plainLabel = false)), initialTab = 1, guideSections = emptyList())
        onAllNodesWithText(strings.watchTutorialVideo).assertCountEquals(1)
    }

    @Test fun tutorialVideosSectionListsVideosAndHidesWhenEmpty() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened, initialTab = 1)
        onNodeWithText(strings.helpTutorialVideos).assertExists()
        onNodeWithText("Episode one").performClick()
        assertEquals(listOf("https://video/e1"), opened)
    }

    @Test fun emptyVideoListHidesTheTutorialVideosHeader() = runComposeUiTest {
        show(videos = emptyList(), initialTab = 1)
        onNodeWithText(strings.helpTutorialVideos).assertDoesNotExist()
    }

    @Test fun catalogWithSharedPlaceholderUrlsStillRendersDistinctClickableRows() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val url = "https://www.youtube.com/watch?v=RlLkpKZZwTg"
        show(
            videos = listOf(VideoRowUi("Episode one", url), VideoRowUi("Episode two", url)),
            opened = opened, initialTab = 1, guideSections = emptyList(),
        )
        onNodeWithText("Episode one").assertExists()
        onNodeWithText("Episode two").performClick()
        assertEquals(listOf(url), opened)
    }

    @Test fun defaultVideoCatalogIncludesEveryEpisodeInSeriesOrder() {
        val deps = net.bible.sharedui.help.nav.HelpNavDeps({}, {}, { emptyList() }, { "Version" }, {})
        val videos = deps.tutorialVideos("fi")
        assertEquals(Episode.entries.map { UserGuideToc.episodeTitles.getValue(it) }, videos.map { it.title })
        assertEquals(setOf("https://www.youtube.com/watch?v=RlLkpKZZwTg&cc_load_policy=1&cc_lang_pref=fi&hl=fi"), videos.map { it.url }.toSet())
        kotlin.test.assertTrue(videos.all { it.plainLabel })
    }

    @Test fun upCallsOnUp() = runComposeUiTest {
        var up = 0
        show(onUp = { up++ })
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()
        assertEquals(1, up)
    }
}
