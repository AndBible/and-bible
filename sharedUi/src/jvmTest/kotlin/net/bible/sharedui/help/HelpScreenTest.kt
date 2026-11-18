package net.bible.sharedui.help

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.bible.sharedui.desktop.reading.DesktopBackDispatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HelpScreenTest {
    private fun episodeTitle(episode: Episode): String = UserGuideToc.episodeTitles.getValue(episode).title
    private fun numberedTitle(episode: Episode): String = UserGuideToc.episodeTitles.getValue(episode).let {
        if (it.number != null) "${it.number}. ${it.title}" else it.title
    }
    private val strings = iosStrings("en")

    private val withVideo = UserGuideToc.Page("navigation", "Navigation page", "navigation_key", "https://x/navigation/", listOf(Episode.TUTORIAL_02_FINDING_YOUR_WAY))
    private val noVideo = UserGuideToc.Page("faq", "Faq page", "faq_key", "https://x/faq/", listOf(Episode.TUTORIAL_03_VERSE_MENU))
    private val sections = listOf(UserGuideToc.Section("Section One", "section_key", listOf(withVideo, noVideo)))
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
        guideStrings: net.bible.sharedui.strings.keyed.KeyedUserGuideStrings = net.bible.sharedui.strings.keyed.KeyedUserGuideStrings.english(),
        uiStrings: net.bible.sharedui.strings.Strings = strings,
    ) = setContent {
        CompositionLocalProvider(LocalStrings provides uiStrings) {
            AbTheme(darkTheme = false) {
                HelpScreen(
                    tips = tips, sections = guideSections, tutorialVideos = videos, versionText = "Version 9.9",
                    guideStrings = guideStrings, appLanguage = appLanguage, initialTab = initialTab, onOpenUrl = { opened += it }, onUp = onUp,
                )
            }
        }
    }

    @Test fun translatedGuideAndCatalogTitlesReachTheScreen() = runComposeUiTest {
        val episode = Episode.TUTORIAL_02_FINDING_YOUR_WAY
        val episodeKey = UserGuideToc.episodeTitles.getValue(episode).key
        val translated = mapOf("section_key" to "Translated section", "navigation_key" to "Translated page", episodeKey to "Translated video")
        val table = net.bible.sharedui.strings.keyed.StringTable.of("fi", { translated[it] ?: it }, { key, _ -> key }, { emptyList() })
        val guideStrings = net.bible.sharedui.strings.keyed.KeyedUserGuideStrings(table)
        val deps = net.bible.sharedui.help.nav.HelpNavDeps({}, {}, { emptyList() }, { "Version" }, {}, guideStrings = guideStrings)
        assertEquals("2. Translated video", deps.tutorialVideos("fi")[Episode.entries.indexOf(episode)].title)
        show(videos = emptyList(), initialTab = 1, guideStrings = guideStrings)
        onNodeWithText("Translated section").assertExists()
        onNodeWithText("Translated page").assertExists()
        onNodeWithText("Translated video").assertExists()
        onNodeWithText("2. Translated video").assertDoesNotExist()
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

    @Test fun guidePageHasSupportingTextAndAccessibleClick() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(videos = emptyList(), opened = opened, initialTab = 1)
        val page = onNodeWithText("Navigation page")
        onAllNodesWithText("Read in the user guide").assertCountEquals(3)
        val click = page.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)
        assertEquals("Read in the user guide: Navigation page", click?.label)
        page.performClick()
        assertEquals(listOf("https://x/navigation/"), opened)
    }

    @Test fun guidePageClickLabelUsesTheCompleteLocalizedTemplate() = runComposeUiTest {
        val localized = object : net.bible.sharedui.strings.Strings by strings {
            override fun helpReadPage(title: String) = "<$title> / open guide"
        }
        show(videos = emptyList(), initialTab = 1, uiStrings = localized)
        val click = onNodeWithText("Navigation page").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)
        assertEquals("<Navigation page> / open guide", click?.label)
    }

    @Test fun guidePageAndEpisodesAreOrderedAndAligned() = runComposeUiTest {
        val first = withVideo.copy(episodes = listOf(Episode.TUTORIAL_02_FINDING_YOUR_WAY, Episode.TUTORIAL_03_VERSE_MENU))
        val second = noVideo.copy(episodes = emptyList())
        show(videos = emptyList(), initialTab = 1, guideSections = listOf(UserGuideToc.Section("Section One", "section_key", listOf(first, second))))
        val page = onNodeWithText("Navigation page", useUnmergedTree = true).getBoundsInRoot()
        val episode1 = onNodeWithText(episodeTitle(Episode.TUTORIAL_02_FINDING_YOUR_WAY), useUnmergedTree = true).getBoundsInRoot()
        val episode2 = onNodeWithText(episodeTitle(Episode.TUTORIAL_03_VERSE_MENU), useUnmergedTree = true).getBoundsInRoot()
        val next = onNodeWithText("Faq page", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(page.top < episode1.top && episode1.top < episode2.top && episode2.top < next.top)
        assertEquals(page.left, episode1.left)
        assertEquals(page.left, episode2.left)
    }

    @Test fun unpublishedEpisodesGetPlayableRowsUnderTheirPages() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened)
        onNodeWithText(strings.helpTabUserGuide).performClick()
        val title = episodeTitle(Episode.TUTORIAL_02_FINDING_YOUR_WAY)
        onAllNodesWithText(title).assertCountEquals(1)
        onNodeWithText(episodeTitle(Episode.TUTORIAL_03_VERSE_MENU)).assertExists()
        onNodeWithText(title).performClick()
        assertEquals(listOf("https://www.youtube.com/watch?v=RlLkpKZZwTg"), opened)
    }

    @Test fun episodeRowsUseTheAppLanguageForUrlAndLabel() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val episode = Episode.TUTORIAL_02_FINDING_YOUR_WAY
        show(videos = emptyList(), opened = opened, appLanguage = "fi")
        onNodeWithText(strings.helpTabUserGuide).performClick()
        onNodeWithText(episodeTitle(episode)).assertExists()
        onAllNodesWithText(strings.watchTutorialVideoPlain).assertCountEquals(2)
        onNodeWithText(episodeTitle(episode)).performClick()
        assertEquals(listOf("https://www.youtube.com/watch?v=RlLkpKZZwTg&cc_load_policy=1&cc_lang_pref=fi&hl=fi"), opened)
    }

    @Test fun videoRowWithoutCaptionsCarriesTheEnglishQualifier() = runComposeUiTest {
        show(videos = listOf(VideoRowUi("Episode one", "https://video/e1", plainLabel = false)), initialTab = 1, guideSections = emptyList())
        onNodeWithText(strings.helpWatchInOrder).performClick()
        onAllNodesWithText(strings.watchTutorialVideo).assertCountEquals(1)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.openInOrder() {
        onNodeWithText(strings.helpTabUserGuide).performClick()
        onNodeWithText(strings.helpWatchInOrder).performClick()
    }

    @Test fun inOrderRowIsFirstAndTheTocHasNoTrailingCatalog() = runComposeUiTest {
        show(initialTab = 1)
        val inOrder = onNodeWithText(strings.helpWatchInOrder, useUnmergedTree = true).getBoundsInRoot()
        val home = onNodeWithText(strings.helpUserGuideHome, useUnmergedTree = true).getBoundsInRoot()
        val firstSection = onNodeWithText("Section One", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(inOrder.top < home.top && home.top < firstSection.top)
        onNodeWithText(strings.helpTutorialVideos).assertDoesNotExist()
        onNodeWithText("Episode one").assertDoesNotExist()
    }

    @Test fun inOrderRowIsHiddenWhenThereAreNoTutorialVideos() = runComposeUiTest {
        show(videos = emptyList(), initialTab = 1)
        onNodeWithText(strings.helpUserGuideHome).assertExists()
        onNodeWithText(strings.helpWatchInOrder).assertDoesNotExist()
    }

    @Test fun tocEpisodeRowsAreUnnumbered() = runComposeUiTest {
        show(initialTab = 1)
        onNodeWithText(episodeTitle(Episode.TUTORIAL_02_FINDING_YOUR_WAY)).assertExists()
        onNodeWithText(numberedTitle(Episode.TUTORIAL_02_FINDING_YOUR_WAY)).assertDoesNotExist()
    }

    @Test fun inOrderRowOpensTheSeriesSubViewAndUpReturnsToToc() = runComposeUiTest {
        var up = 0
        show(onUp = { up++ }, initialTab = 1)
        onNodeWithText(strings.helpWatchInOrder).performClick()
        onNodeWithText("Episode one").assertExists()
        onNodeWithText("Section One").assertDoesNotExist()
        onNodeWithText(strings.helpTutorialVideos).assertExists()
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()
        assertEquals(0, up)
        onNodeWithText("Section One").assertExists()
        onNodeWithText(strings.helpWatchInOrder).assertExists()
        onNodeWithText("Episode one").assertDoesNotExist()
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()
        assertEquals(1, up)
    }

    @Test fun subViewKeepsTheUserGuideTabSelectedAfterReturning() = runComposeUiTest {
        show()
        openInOrder()
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()
        onNodeWithText("Section One").assertExists()
        onNodeWithText("Tip title").assertDoesNotExist()
    }

    @Test fun subViewOpensVideoUrls() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(opened = opened)
        openInOrder()
        onNodeWithText("Episode one").performClick()
        assertEquals(listOf("https://video/e1"), opened)
    }

    @Test fun subViewListsTheWholeSeriesNumberedInOrder() = runComposeUiTest {
        val deps = net.bible.sharedui.help.nav.HelpNavDeps({}, {}, { emptyList() }, { "Version" }, {})
        val videos = deps.tutorialVideos("en")
        assertEquals(Episode.entries.map { numberedTitle(it) }, videos.map { it.title })
        assertEquals(episodeTitle(Episode.WHATS_NEW_6_0), videos.single { !it.title.first().isDigit() }.title)
        show(videos = videos.take(2))
        openInOrder()
        val b1 = onNodeWithText(videos[0].title, useUnmergedTree = true).getBoundsInRoot()
        val b2 = onNodeWithText(videos[1].title, useUnmergedTree = true).getBoundsInRoot()
        assertTrue(b1.top < b2.top)
    }

    @Test fun catalogWithSharedPlaceholderUrlsStillRendersDistinctClickableRows() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val url = "https://www.youtube.com/watch?v=RlLkpKZZwTg"
        show(
            videos = listOf(VideoRowUi("Episode one", url), VideoRowUi("Episode two", url)),
            opened = opened, initialTab = 1, guideSections = emptyList(),
        )
        onNodeWithText(strings.helpWatchInOrder).performClick()
        onNodeWithText("Episode one").assertExists()
        onNodeWithText("Episode two").performClick()
        assertEquals(listOf(url), opened)
    }

    @Test fun defaultVideoCatalogIncludesEveryEpisodeInSeriesOrder() {
        val deps = net.bible.sharedui.help.nav.HelpNavDeps({}, {}, { emptyList() }, { "Version" }, {})
        val videos = deps.tutorialVideos("fi")
        assertEquals(Episode.entries.map { numberedTitle(it) }, videos.map { it.title })
        assertEquals(setOf("https://www.youtube.com/watch?v=RlLkpKZZwTg&cc_load_policy=1&cc_lang_pref=fi&hl=fi"), videos.map { it.url }.toSet())
        kotlin.test.assertTrue(videos.all { it.plainLabel })
    }

    @Test fun upCallsOnUp() = runComposeUiTest {
        var up = 0
        show(onUp = { up++ })
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()
        assertEquals(1, up)
    }

    @Test fun systemBackInTheSubViewReturnsToTheTocWithoutLeavingTheScreen() = runComposeUiTest {
        var up = 0
        show(onUp = { up++ }, initialTab = 1)
        onNodeWithText(strings.helpWatchInOrder).performClick()
        onNodeWithText("Episode one").assertExists()
        runOnIdle { assertTrue(DesktopBackDispatcher.back()) }
        waitForIdle()
        onNodeWithText(strings.helpWatchInOrder).assertExists()
        onNodeWithText(strings.helpTabUserGuide).assertExists()
        onNodeWithText("Episode one").assertDoesNotExist()
        assertEquals(0, up)
    }

    @Test fun systemBackOutsideTheSubViewIsNotConsumedByTheScreen() = runComposeUiTest {
        var up = 0
        show(onUp = { up++ }, initialTab = 1)
        runOnIdle { assertFalse(DesktopBackDispatcher.back()) }
        onNodeWithText(strings.helpWatchInOrder).assertExists()
        assertEquals(0, up)
    }

    @Test fun subViewSurvivesSavedStateRestore() = runComposeUiTest {
        // StateRestorationTester is not available on the skiko test target, so save and restore through a
        // SaveableStateRegistry by hand: what the Activity does across a rotation.
        var registry by mutableStateOf<SaveableStateRegistry?>(SaveableStateRegistry(null) { true })
        setContent {
            // The same composition path is disposed and rebuilt: rememberSaveable keys depend on it.
            registry?.let { reg ->
                CompositionLocalProvider(LocalSaveableStateRegistry provides reg, LocalStrings provides strings) {
                    AbTheme(darkTheme = false) {
                        HelpScreen(
                            tips = tips, sections = sections,
                            tutorialVideos = listOf(VideoRowUi("Episode one", "https://video/e1")),
                            versionText = "Version 9.9",
                            guideStrings = net.bible.sharedui.strings.keyed.KeyedUserGuideStrings.english(),
                            appLanguage = "en", initialTab = 1, onOpenUrl = {}, onUp = {},
                        )
                    }
                }
            }
        }
        onNodeWithText(strings.helpWatchInOrder).performClick()
        onNodeWithText("Episode one").assertExists()
        var saved: Map<String, List<Any?>> = emptyMap()
        runOnIdle { saved = registry!!.performSave() }
        registry = null
        waitForIdle()
        onNodeWithText("Episode one").assertDoesNotExist()
        registry = SaveableStateRegistry(saved) { true }
        waitForIdle()
        onNodeWithText(strings.helpTutorialVideos).assertExists()
        onNodeWithText("Episode one").assertExists()
        onNodeWithText("Section One").assertDoesNotExist()
    }

}
