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
    @Test fun fullWidthRailReservesEveryBottomWindowAndHidingPreservesSystemInsets() {
        rail = true
        val i = insets()
        i.setRailCoveredWindows(setOf("left", "middle", "right"))
        for (id in listOf("left", "middle", "right")) {
            assertEquals(92, i.bottomOffsetForWindow(id, true))
            assertEquals(0, i.bottomOffsetForWindow(id, false))
        }
        // Fullscreen, maximised or single-window: the setting may stay on, but the rail is not drawn.
        i.setRailCoveredWindows(emptySet())
        for (id in listOf("left", "middle", "right")) {
            assertEquals(39, i.bottomOffsetForWindow(id, true))
        }
        rail = false
        transport = true
        assertEquals(0, i.bottomOffsetForWindow("left", true))
    }

    @Test fun reportedBottomWindowsReplaceTheHostPortraitRule() {
        rail = true
        val i = insets()
        i.setRailCoveredWindows(setOf("left", "right"))
        // Before a layout report the host's rule decides.
        assertEquals(0, i.bottomOffsetForWindow("left", false))
        i.setRailLayout(setOf("left", "right"), setOf("left", "right"))
        assertEquals(92, i.bottomOffsetForWindow("left", false))
        assertEquals(92, i.bottomOffsetForWindow("right", false))
        i.setRailLayout(setOf("left", "right"), setOf("right"))
        assertEquals(0, i.bottomOffsetForWindow("left", true))
        assertEquals(92, i.bottomOffsetForWindow("right", false))
        i.setRailLayout(setOf("left", "right"), null)
        assertEquals(92, i.bottomOffsetForWindow("left", true))
    }

    @Test fun railLayoutPushesOffsetsOnlyWhenReservedWindowsChange() {
        val i = insets()
        val seen = mutableListOf<OffsetsChange>()
        val sub = i.offsetsChanged.subscribe { seen += it }
        try {
            i.setRailCoveredWindows(setOf("right"))
            i.setRailCoveredWindows(setOf("right"))
            i.setRailCoveredWindows(emptySet())
            assertEquals(listOf(OffsetsChange(true), OffsetsChange(true)), seen)
        } finally { sub.cancel() }
    }

    @Test fun theSpeakBarTogglingAsksBibleViewForImmediateOffsets() {
        val i = insets()
        val seen = mutableListOf<OffsetsChange>()
        val sub = i.offsetsChanged.subscribe { seen += it }
        i.onTransportVisibilityChanged()
        sub.cancel()
        assertEquals(listOf(OffsetsChange(immediate = true)), seen)
    }
}
