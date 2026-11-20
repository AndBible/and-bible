package net.bible.sharedui.help

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.LocalDisableAnimations
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4159: Help & tips opened for a topic shows the User guide tab, scrolled to the topic's page, highlighted. */
@OptIn(ExperimentalTestApi::class)
class HelpScreenHighlightTest {
    private val strings = iosStrings("en")
    private val highlighted = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, strings.helpMatchingSection)

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        highlight: DocsLink?,
        opened: MutableList<String> = mutableListOf(),
        initialTab: HelpTab = HelpTab.UserGuide,
        noAnimations: Boolean = false,
    ) = setContent {
        CompositionLocalProvider(LocalStrings provides strings, LocalDisableAnimations provides noAnimations) {
            AbTheme(darkTheme = false) {
                HelpScreen(
                    tips = listOf(HelpTipUi("Tip title", "text", null, "", null)),
                    sections = UserGuideToc.sections,
                    tutorialVideos = emptyList(),
                    versionText = "Version",
                    initialTab = initialTab,
                    highlight = highlight,
                    onOpenUrl = { opened += it },
                    onUp = {},
                )
            }
        }
    }

    @Test fun aTopicFarDownTheGuideIsScrolledIntoViewAndHighlighted() = runComposeUiTest {
        show(DocsLink.SUPPORT)
        onNodeWithText("Support").assertIsDisplayed()
        onAllNodes(highlighted).assertCountEquals(1)
        onNodeWithText("Support").assert(highlighted)
    }

    @Test fun thePageAndItsTutorialRowsAreHighlightedAndNothingElse() = runComposeUiTest {
        show(DocsLink.CLOUD_SYNC)
        onNodeWithText("Cloud Sync").assertIsDisplayed().assert(highlighted)
        // Cloud Sync has two episodes: page + 2 rows.
        onAllNodes(highlighted).assertCountEquals(3)
    }

    @Test fun aTopicOpensTheUserGuideTabEvenWhenTipsWasAsked() = runComposeUiTest {
        show(DocsLink.SPEAK, initialTab = HelpTab.Tips)
        onNodeWithText("Tip title").assertDoesNotExist()
        // The page and its episode share the title; both rows are marked, the page row first.
        onAllNodesWithText("Speak: listen to the Bible").assertCountEquals(2).onFirst().assertIsDisplayed().assert(highlighted)
    }

    @Test fun anAnchoredTopicHighlightsItsPageAndTheRowOpensTheAnchor() = runComposeUiTest {
        val opened = mutableListOf<String>()
        show(DocsLink.AI_SETTING_PERMISSIONS, opened)
        onNodeWithText("AI Bible study assistant").assertIsDisplayed().assert(highlighted).performClick()
        assertEquals(listOf(DocsLink.AI_SETTING_PERMISSIONS.url), opened)
    }

    @Test fun withAnimationsDisabledTheTopicIsStillScrolledToAndHighlighted() = runComposeUiTest {
        show(DocsLink.FAQ, noAnimations = true)
        onNodeWithText("Frequently Asked Questions").assertIsDisplayed().assert(highlighted)
    }

    @Test fun withoutATopicNothingIsHighlighted() = runComposeUiTest {
        show(null)
        onAllNodes(highlighted).assertCountEquals(0)
    }

    /** The real screen has the "watch in order" row above the guide; the scroll index must account for it. */
    @Test fun withTheWatchInOrderRowTheTopicIsStillScrolledTo() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    HelpScreen(
                        tips = emptyList(), sections = UserGuideToc.sections,
                        tutorialVideos = listOf(VideoRowUi("Episode", "https://v")), versionText = "V",
                        highlight = DocsLink.FAQ, onOpenUrl = {}, onUp = {},
                    )
                }
            }
        }
        onNodeWithText("Frequently Asked Questions").assertIsDisplayed().assert(highlighted)
    }

    /** #4159 review: a topic delivered to an already open screen (launchSingleTop) must leave Tips / the videos view. */
    private fun androidx.compose.ui.test.ComposeUiTest.showWithLateTopic(
        initialTab: HelpTab, initialShowVideos: Boolean,
    ): (DocsLink) -> Unit {
        var topic by mutableStateOf<DocsLink?>(null)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    HelpScreen(
                        tips = listOf(HelpTipUi("Tip title", "text", null, "", null)),
                        sections = UserGuideToc.sections,
                        tutorialVideos = listOf(VideoRowUi("Episode one", "https://v")),
                        versionText = "Version",
                        initialTab = initialTab, initialShowVideos = initialShowVideos,
                        highlight = topic, onOpenUrl = {}, onUp = {},
                    )
                }
            }
        }
        return { topic = it }
    }

    @Test fun aTopicDeliveredWhileOnTipsSwitchesToTheGuideAndScrolls() = runComposeUiTest {
        val deliver = showWithLateTopic(HelpTab.Tips, initialShowVideos = false)
        onNodeWithText("Tip title").assertIsDisplayed()
        deliver(DocsLink.SUPPORT)
        waitForIdle()
        onNodeWithText("Tip title").assertDoesNotExist()
        onNodeWithText("Support").assertIsDisplayed().assert(highlighted)
    }

    @Test fun aTopicDeliveredWhileInTheVideosViewReturnsToTheGuide() = runComposeUiTest {
        val deliver = showWithLateTopic(HelpTab.UserGuide, initialShowVideos = true)
        onNodeWithText("Episode one").assertIsDisplayed()
        deliver(DocsLink.SUPPORT)
        waitForIdle()
        onNodeWithText("Support").assertIsDisplayed().assert(highlighted)
    }

    @Test fun guideItemIndexCountsEveryRowBeforeThePage() {
        val sections = UserGuideToc.sections
        val first = sections.first().pages.first()
        // intro, home, header -> 3 (and 4 with the watch-in-order row)
        assertEquals(3, guideItemIndex(sections, hasWatchInOrder = false, stem = first.stem))
        assertEquals(4, guideItemIndex(sections, hasWatchInOrder = true, stem = first.stem))
        val second = sections.first().pages[1]
        assertEquals(4 + 1 + first.episodes.size, guideItemIndex(sections, hasWatchInOrder = true, stem = second.stem))
        assertEquals(null, guideItemIndex(sections, hasWatchInOrder = true, stem = "nope"))
    }
}
