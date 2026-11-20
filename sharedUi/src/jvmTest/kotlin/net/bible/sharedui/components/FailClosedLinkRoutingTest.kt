package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FailClosedLinkRoutingTest {
    private class Links(override val askBeforeOpening: Boolean) : ExternalLinks {
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    @Composable
    private fun BareLink() = AbInfoDialog(
        title = "Help", body = "Body", onDismiss = {},
        readMoreLabel = "Read more", readMoreUrl = "https://example.org/x",
    )

    @Test fun discreteQuestionShowsLiteralUrlAndOpensTheOriginal() = runComposeUiTest {
        val url = "https://example.org/<b>other.org</b>?x=&amp;"
        val strings = iosStrings("en")
        val links = Links(askBeforeOpening = true)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                FailClosedLinkRouting(links) {
                    AbInfoDialog(title = "Help", body = "Body", onDismiss = {}, confirmLabel = "Close help",
                        readMoreLabel = "Read more", readMoreUrl = url)
                }
            }
        }
        onNodeWithText("Read more").performClick()
        onNodeWithText(strings.externalLinkOpenQuestion).assertExists()
        onNodeWithText(url).assertExists()
        onNodeWithText(strings.openAction).performClick()
        kotlin.test.assertEquals(listOf(url), links.opened)
    }

    @Test
    fun asksFirstWhenDiscrete() = runComposeUiTest {
        val links = Links(askBeforeOpening = true)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                FailClosedLinkRouting(links) { BareLink() }
            }
        }
        onNodeWithText("Read more").performClick()
        onNodeWithText(iosStrings("en").externalLinkOpenQuestion).assertExists()
        assertTrue(links.opened.isEmpty(), "must ask before opening")
    }
}
