package net.bible.sharedui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.theme.LocalDisableAnimations
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppToastHostTest {
    @AfterTest
    fun reset() = AppToasts.clear()

    /** A toast appears when shown and is gone after its duration; with animations off it is removed outright. */
    @Test
    fun toastShowsAndHides() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalDisableAnimations provides true) { AppToastHost() } }
        onNodeWithText("Copied").assertDoesNotExist()
        AppToasts.show("Copied")
        waitForIdle()
        onNodeWithText("Copied").assertExists()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS + 100)
        waitForIdle()
        onNodeWithText("Copied").assertDoesNotExist()
    }

    /** A second toast replaces the first and gets its own full duration. */
    @Test
    fun newerToastReplacesTheOlderAndRestartsTheTimer() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalDisableAnimations provides true) { AppToastHost() } }
        AppToasts.show("First")
        waitForIdle()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS - 500)
        AppToasts.show("Second")
        waitForIdle()
        onNodeWithText("First").assertDoesNotExist()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS - 500)
        waitForIdle()
        onNodeWithText("Second").assertExists()
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onNodeWithText("Second").assertDoesNotExist()
    }

    /** The same text twice in a row is two toasts: the second one restarts the timer. */
    @Test
    fun sameTextTwiceRestartsTheTimer() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalDisableAnimations provides true) { AppToastHost() } }
        AppToasts.show("Again")
        waitForIdle()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS - 500)
        AppToasts.show("Again")
        waitForIdle()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS - 500)
        waitForIdle()
        onNodeWithText("Again").assertExists()
    }

    /** A long toast outlives the short duration. */
    @Test
    fun longToastOutlivesTheShortDuration() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalDisableAnimations provides true) { AppToastHost() } }
        AppToasts.show("Long one", long = true)
        waitForIdle()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS + 100)
        waitForIdle()
        onNodeWithText("Long one").assertExists()
        mainClock.advanceTimeBy(AppToasts.LONG_MILLIS)
        waitForIdle()
        onNodeWithText("Long one").assertDoesNotExist()
    }

    /** Blank text is not a toast. */
    @Test
    fun blankTextIsIgnored() {
        AppToasts.show("   ")
        AppToasts.show("")
        kotlin.test.assertEquals(null, AppToasts.state.value)
    }

    /** With animations on, the toast still appears and is gone after its duration plus the fade. */
    @Test
    fun animatedToastShowsAndHides() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalDisableAnimations provides false) { AppToastHost() } }
        AppToasts.show("Fading")
        waitForIdle()
        onNodeWithText("Fading").assertExists()
        mainClock.advanceTimeBy(AppToasts.SHORT_MILLIS + 1500)
        waitForIdle()
        onNodeWithText("Fading").assertDoesNotExist()
    }
}
