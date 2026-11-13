/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.reading.host

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedui.theme.NoMotionDurationScale

/**
 * #4074: the leading-edge swipe that opens the reading view's main menu ([drawerEdgeSwipe] driving an
 * M3 drawer through [DrawerEdgeDrag]), wired as `ReadingHost` wires it: M3's own gestures only while open.
 */
@OptIn(ExperimentalTestApi::class)
class DrawerEdgeSwipeTest {
    private class Harness {
        lateinit var drawerState: DrawerState
        var clicks = 0
        var openRequests = 0
        lateinit var scope: CoroutineScope
    }

    private val width = 600f
    private val height = 700f
    private val sheetWidth = 300f
    private val midY = height / 2

    private fun ComposeUiTest.setHarness(
        rtl: Boolean = false,
        followFinger: Boolean = true,
        zeroMotionScale: Boolean = false,
        leadingInset: Float = 0f,
    ): Harness {
        val h = Harness()
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                h.drawerState = drawerState
                val composeScope = rememberCoroutineScope()
                // Android puts the animator scale (and the app's no-animations setting) in the recomposer's context.
                val scope = if (zeroMotionScale) CoroutineScope(composeScope.coroutineContext + NoMotionDurationScale) else composeScope
                h.scope = scope
                val drag = remember {
                    DrawerEdgeDrag(
                        drawerState = drawerState,
                        scope = scope,
                        velocityThresholdPx = 400f,
                        followFinger = { followFinger },
                        requestOpen = {
                            h.openRequests++
                            scope.launch { drawerState.open() }
                        },
                    )
                }
                Box(Modifier.size(width.dp, height.dp).testTag("root")) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerState.isOpen,
                        drawerContent = { ModalDrawerSheet(Modifier.width(sheetWidth.dp)) { Text("menu") } },
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawerEdgeSwipe(enabled = drawerState.isClosed, rtl = rtl, callbacks = drag, leadingInset = leadingInset),
                        ) {
                            Box(Modifier.fillMaxSize().clickable { h.clicks++ })
                        }
                    }
                }
            }
        }
        waitForIdle()
        return h
    }

    /** A slow drag in [steps] equal moves (16 ms apart), without lifting the finger. */
    private fun TouchInjectionScope.slowMove(dx: Float, dy: Float = 0f, steps: Int = 20) {
        repeat(steps) { moveBy(Offset(dx / steps, dy / steps), delayMillis = 16) }
    }

    /** Lets the drag animation and the settle animation run to the end (the clock is manual in these tests). */
    private fun ComposeUiTest.settle() {
        repeat(5) { mainClock.advanceTimeBy(500) }
    }

    @Test
    fun slowDragFromLeadingEdgeOpensTheDrawer() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            // ~150 px/s, below the fling threshold: it opens because it came past halfway.
            slowMove(250f, steps = 100)
            up()
        }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue)
        assertEquals(0, h.clicks, "the drag must not reach the content as a click")
    }

    @Test
    fun theSheetFollowsTheFingerDuringTheDrag() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        val root = onNodeWithTag("root")
        root.performTouchInput {
            down(Offset(5f, midY))
            slowMove(150f)
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val offset = h.drawerState.currentOffset
        // Closed is -300; the finger moved 150 px, of which up to the touch slop went into recognising it.
        assertTrue(offset > -sheetWidth + 100f && offset <= -sheetWidth + 150f, "offset $offset should follow the finger")
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue, "not settled while the finger is down")
        root.performTouchInput {
            slowMove(-120f, steps = 60)
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(h.drawerState.currentOffset < offset - 80f, "the sheet follows the finger back")
        root.performTouchInput { up() }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue, "released barely open and slowly: closes")
    }

    /**
     * Past halfway the anchored state's internal current value flips to Open, but `DrawerState.isClosed` reads the
     * SETTLED value, so the detector (enabled on `isClosed`, as in `ReadingHost`) stays attached and the drag can
     * still be taken back.
     */
    @Test
    fun dragPastHalfwayAndBackStillCloses() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        val root = onNodeWithTag("root")
        root.performTouchInput {
            down(Offset(5f, midY))
            slowMove(260f, steps = 100)
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(h.drawerState.currentOffset > -sheetWidth / 2, "past halfway: ${h.drawerState.currentOffset}")
        root.performTouchInput {
            slowMove(-220f, steps = 100)
            up()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
        assertEquals(-sheetWidth, h.drawerState.currentOffset)
    }

    @Test
    fun shortSlowDragSnapsBackClosed() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            slowMove(80f, steps = 40)
            up()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
        assertEquals(-sheetWidth, h.drawerState.currentOffset)
    }

    @Test
    fun shortFastFlingOpens() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            swipe(Offset(5f, midY), Offset(100f, midY), durationMillis = 60)
        }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue)
    }

    @Test
    fun dragFromTheMiddleDoesNotOpen() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(200f, midY))
            slowMove(300f)
            up()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
        assertEquals(-sheetWidth, h.drawerState.currentOffset)
    }

    @Test
    fun dragStartingJustOutsideTheStripDoesNotOpen() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(DrawerEdgeSwipeWidth.value + 5f, midY))
            slowMove(300f)
            up()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
    }

    @Test
    fun verticalDragAtTheEdgeIsNotTaken() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, 100f))
            slowMove(dx = 20f, dy = 300f)
            up()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
        assertEquals(-sheetWidth, h.drawerState.currentOffset)
    }

    @Test
    fun tapAtTheEdgeReachesTheContent() = runComposeUiTest {
        val h = setHarness()
        onNodeWithTag("root").performTouchInput { click(Offset(5f, midY)) }
        waitForIdle()
        assertEquals(1, h.clicks)
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
    }

    @Test
    fun rtlOpensFromTheRightEdge() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness(rtl = true)
        onNodeWithTag("root").performTouchInput {
            down(Offset(width - 5f, midY))
            slowMove(-250f, steps = 100)
            up()
        }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue)
    }

    @Test
    fun rtlIgnoresTheLeftEdge() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness(rtl = true)
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            slowMove(250f)
            up()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
    }

    @Test
    fun withAnimationsOffTheDragRequestsOpenInsteadOfFollowing() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness(followFinger = false)
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            slowMove(40f, steps = 4)
        }
        assertEquals(1, h.openRequests)
        onNodeWithTag("root").performTouchInput {
            slowMove(-30f, steps = 3)
            up()
        }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue, "release does not undo the requested open")
        assertEquals(1, h.openRequests)
    }

    @Test
    fun withAZeroMotionScaleASlowShortDragStillOpens() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness(zeroMotionScale = true)
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            slowMove(60f, steps = 30)
            up()
        }
        settle()
        assertEquals(1, h.openRequests, "no finger tracking when animations finish instantly")
        assertEquals(DrawerValue.Open, h.drawerState.currentValue, "the slow short release must not close it again")
    }

    @Test
    fun mouseDragFromTheEdgeIsIgnored() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performMouseInput {
            moveTo(Offset(5f, midY))
            press()
            repeat(20) { moveBy(Offset(12f, 0f)) }
            release()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue)
    }

    @Test
    fun settleDecision() {
        assertTrue(edgeSwipeSettlesOpen(fraction = 0.1f, inwardVelocity = 500f, velocityThreshold = 400f))
        assertFalse(edgeSwipeSettlesOpen(fraction = 0.9f, inwardVelocity = -500f, velocityThreshold = 400f))
        assertTrue(edgeSwipeSettlesOpen(fraction = 0.5f, inwardVelocity = 0f, velocityThreshold = 400f))
        assertFalse(edgeSwipeSettlesOpen(fraction = 0.49f, inwardVelocity = 399f, velocityThreshold = 400f))
    }

    @Test
    fun leadingEdgeIsMirroredForRtl() {
        assertTrue(isInLeadingEdge(x = 20f, width = 600, edgePx = 20f, rtl = false))
        assertFalse(isInLeadingEdge(x = 21f, width = 600, edgePx = 20f, rtl = false))
        assertTrue(isInLeadingEdge(x = 580f, width = 600, edgePx = 20f, rtl = true))
        assertFalse(isInLeadingEdge(x = 10f, width = 600, edgePx = 20f, rtl = true))
    }

    private fun ComposeUiTest.openFromElsewhere(h: Harness) {
        runOnUiThread { h.scope.launch { h.drawerState.open() } }
    }

    @Test
    fun anOpenFromElsewhereWhileTheFingerIsDownIsNotClosedByTheRelease() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            slowMove(40f, steps = 10)
        }
        openFromElsewhere(h)
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue)
        onNodeWithTag("root").performTouchInput { up() }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue, "a short drag's release must not close the opened drawer")
        assertEquals(0f, h.drawerState.currentOffset)
    }

    @Test
    fun anOpenFromElsewhereThatTheLiftInterruptsStillEndsOpen() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            slowMove(40f, steps = 10)
        }
        openFromElsewhere(h)
        mainClock.advanceTimeBy(32)
        onNodeWithTag("root").performTouchInput { up() }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue)
        assertEquals(DrawerValue.Open, h.drawerState.targetValue)
    }

    @Test
    fun aFastDragThatIsCancelledSettlesByPositionNotByVelocity() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness()
        onNodeWithTag("root").performTouchInput {
            down(Offset(5f, midY))
            repeat(6) { moveBy(Offset(10f, 0f), delayMillis = 8) }
            cancel()
        }
        settle()
        assertEquals(DrawerValue.Closed, h.drawerState.currentValue, "a cancelled short fast drag must not open")
    }

    /** A drag that starts while the sheet is still a little open is measured against the Closed anchor, not its start offset. */
    @Test
    fun openFractionIsMeasuredAgainstTheClosedAnchor() {
        assertEquals(0f, edgeOpenFraction(offset = -300f, closedAnchor = -300f))
        assertEquals(0.5f, edgeOpenFraction(offset = -150f, closedAnchor = -300f))
        assertEquals(1f, edgeOpenFraction(offset = 0f, closedAnchor = -300f))
        // Started 40 px open (-260) and moved 140 px more: 180/300 open, not 140/260.
        assertEquals(0.6f, edgeOpenFraction(offset = -120f, closedAnchor = -300f), 1e-6f)
    }

    @Test
    fun theStripIsMeasuredFromTheLeadingInset() = runComposeUiTest {
        mainClock.autoAdvance = false
        val h = setHarness(leadingInset = 48f)
        onNodeWithTag("root").performTouchInput {
            down(Offset(60f, midY))
            slowMove(250f, steps = 100)
            up()
        }
        settle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue)
    }

    @Test
    fun insetStripIsMirroredForRtl() {
        assertTrue(isInLeadingEdge(x = 540f, width = 600, edgePx = 20f, rtl = true, leadingInset = 48f))
        assertFalse(isInLeadingEdge(x = 570f, width = 600, edgePx = 20f, rtl = false, leadingInset = 48f))
        assertTrue(isInLeadingEdge(x = 60f, width = 600, edgePx = 20f, rtl = false, leadingInset = 48f))
        assertFalse(isInLeadingEdge(x = 69f, width = 600, edgePx = 20f, rtl = false, leadingInset = 48f))
    }

    @Test
    fun leadingEdgeOverlapCoversOnlyTheLeadingPaneUpToTheStrip() {
        // A full-width pane: the strip itself.
        assertEquals(20f, leadingEdgeOverlap(paneLeft = 0f, paneRight = 600f, rootWidth = 600f, edgePx = 20f, rtl = false))
        // Landscape notch: the strip starts at the safe-area inset, so it covers inset + edge.
        assertEquals(68f, leadingEdgeOverlap(0f, 600f, 600f, 20f, rtl = false, leadingInset = 48f))
        // A pane that starts inside the inset (the WebView laid out from the safe area) gets the rest of it.
        assertEquals(20f, leadingEdgeOverlap(48f, 600f, 600f, 20f, rtl = false, leadingInset = 48f))
        // The second pane of a split, or a pane right of a leading rail, does not reach the strip.
        assertEquals(0f, leadingEdgeOverlap(300f, 600f, 600f, 20f, rtl = false))
        // A pane narrower than the strip is covered whole, never beyond itself.
        assertEquals(12f, leadingEdgeOverlap(0f, 12f, 600f, 20f, rtl = false))
    }

    @Test
    fun leadingEdgeOverlapIsMirroredForRtl() {
        assertEquals(20f, leadingEdgeOverlap(0f, 600f, 600f, 20f, rtl = true))
        assertEquals(68f, leadingEdgeOverlap(0f, 600f, 600f, 20f, rtl = true, leadingInset = 48f))
        assertEquals(0f, leadingEdgeOverlap(0f, 300f, 600f, 20f, rtl = true))
        assertEquals(20f, leadingEdgeOverlap(300f, 600f, 600f, 20f, rtl = true))
    }

    /**
     * iOS: a native interop view (the WKWebView) takes every touch that starts on it, so the panes lay
     * [DrawerEdgeHitTarget] over their part of the edge strip. Only the leading pane gets one, it does not
     * consume (the ancestor's edge swipe still opens the drawer) and touches outside it still reach the pane.
     */
    private fun ComposeUiTest.setSplitHarness(rtl: Boolean = false, leadingInset: Float = 0f): Harness {
        val h = Harness()
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                h.drawerState = drawerState
                val scope = rememberCoroutineScope()
                h.scope = scope
                val drag = remember {
                    DrawerEdgeDrag(drawerState, scope, 400f, followFinger = { true }, requestOpen = { scope.launch { drawerState.open() } })
                }
                Box(Modifier.size(width.dp, height.dp).testTag("root")) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerState.isOpen,
                        drawerContent = { ModalDrawerSheet(Modifier.width(sheetWidth.dp)) { Text("menu") } },
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .drawerEdgeSwipe(enabled = drawerState.isClosed, rtl = rtl, callbacks = drag, leadingInset = leadingInset),
                        ) {
                            repeat(2) { pane ->
                                Box(Modifier.weight(1f).fillMaxHeight().testTag("pane$pane")) {
                                    Box(Modifier.fillMaxSize().clickable { h.clicks++ })
                                    DrawerEdgeHitTarget(leadingInset = leadingInset, modifier = Modifier.testTag("strip$pane"))
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        return h
    }

    @Test
    fun hitTargetCoversOnlyTheLeadingPanesEdgeStrip() = runComposeUiTest {
        setSplitHarness(leadingInset = 48f)
        val strip = onNodeWithTag("strip0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(0f, strip.left)
        assertEquals(68f, strip.right)
        assertEquals(height, strip.height)
        assertEquals(0f, onNodeWithTag("strip1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width)
    }

    @Test
    fun hitTargetIsOnTheRightPaneForRtl() = runComposeUiTest {
        setSplitHarness(rtl = true)
        // The Row lays the first (leading) pane out on the right.
        assertEquals(0f, onNodeWithTag("strip1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width)
        val strip = onNodeWithTag("strip0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(width - 20f, strip.left)
        assertEquals(width, strip.right)
    }

    @Test
    fun hitTargetPassesTheEdgeSwipeOnAndLeavesTheRestOfThePane() = runComposeUiTest {
        val h = setSplitHarness()
        onNodeWithTag("root").performTouchInput { click(Offset(40f, midY)) }
        assertEquals(1, h.clicks, "a tap outside the strip reaches the pane")
        onNodeWithTag("root").performTouchInput { click(Offset(400f, midY)) }
        assertEquals(2, h.clicks, "the second pane has no strip")
        onNodeWithTag("root").performTouchInput { click(Offset(5f, midY)) }
        assertEquals(2, h.clicks, "the strip is the hit target there (on iOS: instead of the web view)")
        onNodeWithTag("root").performTouchInput { swipe(Offset(5f, midY), Offset(400f, midY), durationMillis = 200) }
        waitForIdle()
        assertEquals(DrawerValue.Open, h.drawerState.currentValue, "the edge swipe over the strip opens the drawer")
    }
}
