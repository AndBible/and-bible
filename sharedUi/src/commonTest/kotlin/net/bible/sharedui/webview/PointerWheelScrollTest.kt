package net.bible.sharedui.webview

import kotlin.test.Test
import kotlin.test.assertEquals

/** The wheel/trackpad scroll arithmetic the iOS pane uses to scroll its WKWebView from Compose `Scroll` pointer events. */
class PointerWheelScrollTest {
    @Test
    fun composeScrollDeltaConvertsBackToPoints() {
        // CMP iOS sends scrollDelta = points * density * 0.01; a 30 pt pan on a 2x screen arrives as 0.6.
        assertEquals(30.0, PointerWheelScroll.deltaPoints(scrollDelta = 0.6f, density = 2f), 1e-4)
        assertEquals(-12.0, PointerWheelScroll.deltaPoints(scrollDelta = -0.12f, density = 1f), 1e-4)
    }

    @Test
    fun zeroDensityGivesNoMovement() {
        assertEquals(0.0, PointerWheelScroll.deltaPoints(scrollDelta = 1f, density = 0f))
    }

    @Test
    fun offsetMovesByDeltaInsideTheContent() {
        val r = PointerWheelScroll.scrolledOffset(current = 100.0, delta = 40.0, contentLength = 2000.0, viewportLength = 800.0, insetStart = 0.0, insetEnd = 0.0)
        assertEquals(140.0, r)
    }

    @Test
    fun offsetStopsAtTheEndOfTheContent() {
        val r = PointerWheelScroll.scrolledOffset(current = 1190.0, delta = 50.0, contentLength = 2000.0, viewportLength = 800.0, insetStart = 0.0, insetEnd = 20.0)
        assertEquals(1220.0, r) // 2000 - 800 + bottom inset 20
    }

    @Test
    fun offsetStopsAtTheTopIncludingTheInset() {
        val r = PointerWheelScroll.scrolledOffset(current = 10.0, delta = -500.0, contentLength = 2000.0, viewportLength = 800.0, insetStart = 44.0, insetEnd = 0.0)
        assertEquals(-44.0, r)
    }

    @Test
    fun contentShorterThanTheViewportStaysAtTheTop() {
        val r = PointerWheelScroll.scrolledOffset(current = -44.0, delta = 300.0, contentLength = 500.0, viewportLength = 800.0, insetStart = 44.0, insetEnd = 0.0)
        assertEquals(-44.0, r)
    }
}
