package net.bible.sharedui.desktop.exit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExitSyncStatusTest {
    @Test fun showsTitleBodyAndQuitNow() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncStatus(false, {}) }
        } }
        onNodeWithText("Synchronizing changes…").assertIsDisplayed()
        onNodeWithText("Sending your changes to your other devices. This can take a few seconds.").assertIsDisplayed()
        onNodeWithText("Quit now").assertIsDisplayed()
    }

    @Test fun quitNowCallsCallbackOnce() = runComposeUiTest {
        var calls = 0
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncStatus(false, { calls++ }) }
        } }
        onNodeWithText("Quit now").performClick()
        onNodeWithText("Quit now").performClick()
        assertEquals(1, calls)
    }

    @Test fun closingStateShowsClosingAndDisablesButton() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncStatus(true, {}) }
        } }
        onNodeWithText("Closing…").assertIsDisplayed()
        onNodeWithText("Quit now").assertIsNotEnabled()
    }

    @Test fun noAnimationsShowsStaticIconWithoutPendingFrames() {
        val existingRecomposers = Recomposer.runningRecomposers.value
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = true) { ExitSyncStatus(false, {}) }
            } }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithTag("exitSyncStaticIcon").assertIsDisplayed()
            onNodeWithTag("exitSyncSpinner").assertDoesNotExist()
            runOnUiThread {
                val recomposers = Recomposer.runningRecomposers.value - existingRecomposers
                assertTrue(recomposers.isNotEmpty(), "The test must observe its running recomposer")
                // A frozen test clock can be UI-idle while an animation still awaits its next frame.
                assertFalse(recomposers.any { it.hasPendingWork }, "No-animation content must not await animation frames")
            }
        }
    }
}
