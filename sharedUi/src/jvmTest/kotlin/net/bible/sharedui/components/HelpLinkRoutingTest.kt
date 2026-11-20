package net.bible.sharedui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.HelpLinks
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4159: help links (`andbible-help:`) open Help & tips in the app, wherever a link can be clicked. */
@OptIn(ExperimentalTestApi::class)
class HelpLinkRoutingTest {
    private val strings = iosStrings("en")

    private class Links(override val askBeforeOpening: Boolean) : ExternalLinks {
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        links: Links,
        helpOpener: HelpOpener?,
        content: @Composable () -> Unit,
    ) = setContent {
        CompositionLocalProvider(LocalStrings provides strings, LocalHelpOpener provides helpOpener) {
            FailClosedLinkRouting(links, content)
        }
    }

    @Test fun anHtmlHelpLinkOpensHelpInTheAppWithoutTheDiscreteQuestion() = runComposeUiTest {
        val links = Links(askBeforeOpening = true)
        val topics = mutableListOf<DocsLink>()
        show(links, { topics += it }) { AbHtmlText("<a href=\"${HelpLinks.url(DocsLink.SPEAK)}\">Help</a>") }
        onNodeWithText("Help").performClick()
        assertEquals(listOf(DocsLink.SPEAK), topics)
        assertEquals(emptyList(), links.opened)
        onNodeWithText(strings.externalLink).assertDoesNotExist()
    }

    @Test fun anOrdinaryLinkStillGoesOutside() = runComposeUiTest {
        val links = Links(askBeforeOpening = false)
        val topics = mutableListOf<DocsLink>()
        show(links, { topics += it }) { AbHtmlText("<a href=\"https://example.org/\">Out</a>") }
        onNodeWithText("Out").performClick()
        assertEquals(listOf("https://example.org/"), links.opened)
        assertEquals(emptyList(), topics)
    }

    @Test fun withoutAHelpOpenerAHelpLinkFallsBackToTheGuidePage() = runComposeUiTest {
        val links = Links(askBeforeOpening = false)
        show(links, null) { AbHtmlText("<a href=\"${HelpLinks.url(DocsLink.AI_SETTING_PERMISSIONS)}\">Help</a>") }
        onNodeWithText("Help").performClick()
        assertEquals(listOf(DocsLink.AI_SETTING_PERMISSIONS.url), links.opened)
    }

    @Test fun theHelpMenuItemOpensItsTopic() = runComposeUiTest {
        val topics = mutableListOf<DocsLink>()
        var closed = false
        show(Links(false), { topics += it }) { AbHelpMenuItem(DocsLink.BACKUP_RESTORE) { closed = true } }
        onNodeWithText(strings.helpInUserGuide).performClick()
        assertEquals(listOf(DocsLink.BACKUP_RESTORE), topics)
        assertEquals(true, closed)
    }

    @Test fun anInfoDialogHelpTopicLinkOpensHelp() = runComposeUiTest {
        val topics = mutableListOf<DocsLink>()
        show(Links(true), { topics += it }) {
            AbInfoDialog(title = "T", body = "Body", onDismiss = {}, helpTopic = DocsLink.SEARCH_ADVANCED_SEARCH)
        }
        onNodeWithText(strings.helpInUserGuide).performClick()
        assertEquals(listOf(DocsLink.SEARCH_ADVANCED_SEARCH), topics)
    }

    @Test fun withNoLinkRoutingAtAllTheHelpOpenerIsStillUsed() = runComposeUiTest {
        val topics = mutableListOf<DocsLink>()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings, LocalHelpOpener provides HelpOpener { topics += it }) {
                AbHelpMenuItem(DocsLink.NOTES) {}
                Text("x")
            }
        }
        onNodeWithText(strings.helpInUserGuide).performClick()
        assertEquals(listOf(DocsLink.NOTES), topics)
    }

    @Test fun aHelpLinkInAnAppDialogAnswersTheDialogBeforeOpeningHelp() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings, LocalHelpOpener provides HelpOpener { events += "help:$it" }) {
                AppDialogHost(
                    shown = net.bible.sharedcore.ui.dialog.ShownDialog(7, net.bible.sharedcore.ui.dialog.AppDialogRequest.Message(
                        "Help", "Body <a href=\"${HelpLinks.url(DocsLink.DOCUMENT_SYNC)}\">guide</a>", "OK", cancellable = true,
                    )),
                    permission = null,
                    onRespond = { id, result -> events += "respond:$id:$result" },
                    onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = { events += "external:$it" },
                )
            }
        }
        onNodeWithText("guide", substring = true).performClick()
        assertEquals(listOf("respond:7:${net.bible.sharedcore.ui.dialog.AppDialogResult.Cancel}", "help:${DocsLink.DOCUMENT_SYNC}"), events)
    }

    @Test fun anInfoDialogClosesItselfBeforeOpeningHelp() = runComposeUiTest {
        val events = mutableListOf<String>()
        show(Links(false), { events += "help:$it" }) {
            AbInfoDialog(title = "T", body = "Body", onDismiss = { events += "dismiss" }, helpTopic = DocsLink.LABELS)
        }
        onNodeWithText(strings.helpInUserGuide).performClick()
        assertEquals(listOf("dismiss", "help:${DocsLink.LABELS}"), events)
    }

    @Test fun aHelpLinkWithAnUnknownTopicNeverLeavesAsTheRawScheme() = runComposeUiTest {
        val links = Links(askBeforeOpening = false)
        show(links, null) { AbHtmlText("<a href=\"andbible-help:NO_SUCH\">Help</a>") }
        onNodeWithText("Help").performClick()
        assertEquals(listOf(net.bible.sharedcore.docs.DocsLinks.BASE_URL), links.opened)
    }
}
