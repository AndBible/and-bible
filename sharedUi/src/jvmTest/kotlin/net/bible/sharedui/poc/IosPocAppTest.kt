package net.bible.sharedui.poc

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IosPocAppTest {
    private val created = mutableListOf<String>()
    private val isPane = SemanticsMatcher("test tag starts with pane-") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("pane-") == true
    }

    private val dark = androidx.compose.runtime.mutableStateOf(false)

    private fun androidx.compose.ui.test.ComposeUiTest.show(scenario: PocScenario) = setContent {
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            IosPocApp(scenario, documentJson = "{}", darkTheme = dark.value, onPaneCreated = { created += it })
        }
    }

    @Test fun everyScenarioRenders() {
        for (s in PocScenario.entries) runComposeUiTest {
            show(s)
            waitForIdle()
            if (s.startRoute == "reading") onAllNodes(isPane).assertCountEquals(s.windowCount)
        }
    }

    @Test fun readingToHistoryAndBack() = runComposeUiTest {
        show(PocScenario.SINGLE)
        onNodeWithTag("poc-open-history").performClick()
        onNodeWithText("Ephesians 2:8").assertExists()          // a fake history entry
        onNodeWithTag("pane-w1").assertDoesNotExist()
        onNodeWithTag("poc-back").performClick()                // the PoC row's back button (HistoryScreen has no up action)
        onNodeWithTag("pane-w1").assertExists()
    }

    @Test fun backOnStartDestinationDoesNotBlankTheNavHost() = runComposeUiTest {
        show(PocScenario.HISTORY)
        onNodeWithText("Ephesians 2:8").assertExists()
        onNodeWithTag("poc-back").performClick()   // the only back-stack entry: must not pop it
        mainClock.advanceTimeBy(2_000)             // let any exit transition finish
        waitForIdle()
        onNodeWithText("Ephesians 2:8").assertExists()
    }

    @Test fun paneIdentitySurvivesSplitChanges() = runComposeUiTest {
        show(PocScenario.SPLIT2)
        waitForIdle()
        assertEquals(listOf("w1", "w2"), created)
        onNodeWithTag("poc-split-toggle").performClick()   // 2 -> 3
        waitForIdle()
        onNodeWithTag("poc-split-toggle").performClick()   // 3 -> 2
        waitForIdle()
        assertEquals(listOf("w1", "w2", "w3"), created, "w1/w2 must not be re-created")
    }

    @Test fun themeFlipDoesNotRecreatePanes() = runComposeUiTest {
        show(PocScenario.SPLIT2)
        waitForIdle()
        dark.value = true
        waitForIdle()
        assertEquals(listOf("w1", "w2"), created)
    }

    @Test fun activeWindowIsResetWhenSplitShrinks() = runComposeUiTest {
        show(PocScenario.SPLIT2)
        onNodeWithTag("poc-split-toggle").performClick()   // 2 -> 3
        waitForIdle()
        onNodeWithTag("pane-w3").performClick()            // activates w3 via the real onWindowActivated path
        waitForIdle()
        onNodeWithTag("poc-split-toggle").performClick()   // 3 -> 2
        waitForIdle()
        onNodeWithTag("pane-w3").assertDoesNotExist()
        onNodeWithTag("reading-title").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "w1"))
        onAllNodes(isPane).assertCountEquals(2)
    }
}
