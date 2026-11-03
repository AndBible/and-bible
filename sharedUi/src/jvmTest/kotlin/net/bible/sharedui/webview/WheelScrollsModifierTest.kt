package net.bible.sharedui.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [wheelScrolls]: the Compose `Scroll` events over the iOS reading pane are the only way a mouse wheel / trackpad can
 * move its WKWebView (CMP keeps non-touch UIEvents away from interop views), so they must reach the target in points,
 * only when nothing drawn above the pane takes them, and never for presses.
 */
@OptIn(ExperimentalTestApi::class)
class WheelScrollsModifierTest {
    private class Recorder(private val accepts: Boolean = true) : WheelScrollTarget {
        val calls = mutableListOf<List<Double>>()
        var touching = false
        val diagnostics = mutableListOf<String>()
        override fun scrollBy(xPoints: Double, yPoints: Double, dxPoints: Double, dyPoints: Double): Boolean {
            calls += listOf(xPoints, yPoints, dxPoints, dyPoints)
            return accepts
        }
        override fun isTouchActive(): Boolean = touching
        override fun diagnostic(message: String) { diagnostics += message }
    }

    /** Pane with [target], plus a passive observer of the scroll events' times (the clock of gesture-end signals). */
    private fun ComposeUiTest.pane(target: Recorder, scrollTimes: MutableList<Long> = mutableListOf()) = setContent {
        Box(
            Modifier.size(200.dp).testTag("pane")
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (e.type == PointerEventType.Scroll) scrollTimes += e.changes.first().uptimeMillis
                        }
                    }
                }
                .wheelScrolls(target),
        )
    }

    /** A trackpad swipe: [steps] scroll events 10 ms apart, each moving the content by [delta] (Compose units). */
    private fun ComposeUiTest.swipe(steps: Int = 8, delta: Float = 0.3f, thenZero: Boolean) {
        onNodeWithTag("pane").performMouseInput {
            moveTo(Offset(50f, 50f))
            repeat(steps) {
                advanceEventTime(10)
                scroll(delta)
            }
            if (thenZero) {
                advanceEventTime(5)
                scroll(0f) // CMP's gesture end: the pan's final event, without movement
            }
        }
    }

    @Test
    fun aTrackpadFlickGlidesOnAfterTheFingersLift() = runComposeUiTest {
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.UNKNOWN))
        val target = Recorder()
        pane(target)
        mainClock.autoAdvance = false
        swipe(thenZero = true)
        val moved = target.calls.size
        assertEquals(8, moved, "swipe calls ${target.calls}")
        mainClock.advanceTimeBy(500)
        val glide = target.calls.drop(moved)
        assertTrue(glide.size > 5, "no glide after the lift: ${target.calls}")
        assertTrue(glide.all { it[3] > 0.0 }, "glide went the wrong way: $glide")
        // Decelerating: later frames move less than the first.
        assertTrue(glide.last()[3] < glide.first()[3], "not decelerating: $glide")
        assertTrue(target.diagnostics.any { it.contains("glide") }, "no diagnostic: ${target.diagnostics}")
    }

    @Test
    fun thePlatformEndSignalAlsoStartsTheGlide() = runComposeUiTest {
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.CONTINUOUS))
        val target = Recorder()
        val times = mutableListOf<Long>()
        pane(target, times)
        mainClock.autoAdvance = false
        swipe(thenZero = false)
        mainClock.advanceTimeBy(200)
        assertEquals(8, target.calls.size, "glided without an end: ${target.calls}")
        WheelGestureSignals.emit(WheelGestureSignal.Ended(WheelGestureKind.CONTINUOUS, times.last() + 2, 0.0, 0.0))
        mainClock.advanceTimeBy(300)
        assertTrue(target.calls.size > 12, "no glide after the platform end: ${target.calls}")
    }

    @Test
    fun withAPlatformObserverARestingZeroDeltaDoesNotEndTheGesture() = runComposeUiTest {
        // CMP's zero-delta event is ambiguous (began, ended, or fingers resting); with the observer's signals present
        // only its Ended closes the gesture, so a pause mid-swipe neither glides early nor loses the release velocity.
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.CONTINUOUS))
        val target = Recorder()
        val times = mutableListOf<Long>()
        pane(target, times)
        mainClock.autoAdvance = false
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.CONTINUOUS))
        mainClock.advanceTimeBy(20)
        swipe(thenZero = true)
        mainClock.advanceTimeBy(300)
        assertEquals(8, target.calls.size, "glided before the platform end: ${target.calls}")
        assertTrue(target.diagnostics.isEmpty(), "ended early: ${target.diagnostics}")
    }

    @Test
    fun aMouseWheelDoesNotGlide() = runComposeUiTest {
        val target = Recorder()
        pane(target)
        mainClock.autoAdvance = false
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.DISCRETE))
        mainClock.advanceTimeBy(20)
        swipe(thenZero = true)
        val moved = target.calls.size
        mainClock.advanceTimeBy(500)
        assertEquals(moved, target.calls.size, "wheel glided: ${target.calls.drop(moved)}")
    }

    @Test
    fun touchingTheTrackpadAgainStopsTheGlide() = runComposeUiTest {
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.UNKNOWN))
        val target = Recorder()
        pane(target)
        mainClock.autoAdvance = false
        swipe(thenZero = true)
        mainClock.advanceTimeBy(100)
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.CONTINUOUS))
        mainClock.advanceTimeBy(50)
        val stopped = target.calls.size
        mainClock.advanceTimeBy(500)
        assertEquals(stopped, target.calls.size, "glide went on after a new gesture began")
    }

    @Test
    fun aFingerOnThePageStopsTheGlide() = runComposeUiTest {
        WheelGestureSignals.emit(WheelGestureSignal.Began(WheelGestureKind.UNKNOWN))
        val target = Recorder()
        pane(target)
        mainClock.autoAdvance = false
        swipe(thenZero = true)
        mainClock.advanceTimeBy(100)
        target.touching = true
        mainClock.advanceTimeBy(50)
        val stopped = target.calls.size
        mainClock.advanceTimeBy(500)
        assertEquals(stopped, target.calls.size, "glide fought a touch drag")
    }

    @Test
    fun wheelOverThePaneScrollsTheTargetInPoints() = runComposeUiTest {
        val target = Recorder()
        setContent { Box(Modifier.size(200.dp).testTag("pane").wheelScrolls(target)) }
        onNodeWithTag("pane").performMouseInput {
            moveTo(Offset(50.dp.toPx(), 80.dp.toPx()))
            scroll(0.6f)
        }
        waitForIdle()
        assertEquals(1, target.calls.size, "calls ${target.calls}")
        val (x, y, dx, dy) = target.calls.single()
        // The pointer position arrives in points (= dp here), relative to the pane: the page's elementFromPoint input.
        assertEquals(50.0, x, 1e-3)
        assertEquals(80.0, y, 1e-3)
        assertEquals(0.0, dx)
        assertEquals(0.6 / 0.01 / density.density, dy, 1e-3)
    }

    @Test
    fun aTargetThatDeclinesLeavesTheScrollToItsParent() = runComposeUiTest {
        val target = Recorder(accepts = false)
        val parent = Recorder()
        setContent { Box(Modifier.size(200.dp).wheelScrolls(parent)) { Box(Modifier.fillMaxSize().testTag("pane").wheelScrolls(target)) } }
        onNodeWithTag("pane").performMouseInput { scroll(1f) }
        waitForIdle()
        assertEquals(1, target.calls.size)
        assertEquals(1, parent.calls.size, "declined scroll was consumed")
    }

    @Test
    fun aConsumedScrollIsNotScrolledAgain() = runComposeUiTest {
        val target = Recorder()
        val parent = Recorder()
        setContent { Box(Modifier.size(200.dp).wheelScrolls(parent)) { Box(Modifier.fillMaxSize().testTag("pane").wheelScrolls(target)) } }
        onNodeWithTag("pane").performMouseInput { scroll(1f) }
        waitForIdle()
        assertEquals(1, target.calls.size)
        assertTrue(parent.calls.isEmpty(), "consumed scroll reached the parent: ${parent.calls}")
    }

    @Test
    fun aLayerAboveThePaneKeepsItsScrolls() = runComposeUiTest {
        val target = Recorder()
        setContent {
            Box(Modifier.size(200.dp)) {
                Box(Modifier.fillMaxSize().wheelScrolls(target))
                // A sheet/menu over the pane with its own pointer handling (consumes nothing, but is hit first).
                Box(Modifier.fillMaxSize().testTag("sheet").pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
            }
        }
        onNodeWithTag("sheet").performMouseInput { scroll(1f) }
        waitForIdle()
        assertTrue(target.calls.isEmpty(), "pane scrolled under a layer: ${target.calls}")
    }

    @Test
    fun clicksDoNotScroll() = runComposeUiTest {
        val target = Recorder()
        setContent { Box(Modifier.size(200.dp).testTag("pane").wheelScrolls(target)) }
        onNodeWithTag("pane").performMouseInput { click() }
        waitForIdle()
        assertTrue(target.calls.isEmpty(), "click scrolled: ${target.calls}")
    }
}
