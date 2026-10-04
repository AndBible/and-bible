package net.bible.android.view.activity.page

import androidx.core.graphics.Insets
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fix batch 5 F111: the Speak bar sits below the panes and pads the nav bar itself. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingInsetsTest {
    private var transport = false
    private var rail = false

    private fun insets() = ReadingInsets(ReadingInsetsHostCallbacks(
        transportBarVisible = { transport }, transportBarHeight = { 100 },
        agentLogVisible = { false }, agentLogHeight = { 0 },
        restoreButtonsVisible = { rail }, windowButtonHeight = { 53 },
        composeSearchFieldFocused = { false }, applyImeBottomPadding = {},
    )).apply { onWindowInsetsApplied(Insets.of(0, 0, 0, 39), Insets.NONE) }

    @Test fun withTheSpeakBarUpTheWebViewNeedsNoBottomOffset() { transport = true; assertEquals(0, insets().bottomOffsetForWebView) }
    @Test fun withTheBarHiddenTheNavBarIsTheOffset() { assertEquals(39, insets().bottomOffsetForWebView) }
    @Test fun withTheBarAndTheRailOnlyTheRailCounts() { transport = true; rail = true; assertEquals(53, insets().bottomOffsetForWebView) }
}
