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
        override fun scrollBy(xPoints: Double, yPoints: Double, dxPoints: Double, dyPoints: Double): Boolean {
            calls += listOf(xPoints, yPoints, dxPoints, dyPoints)
            return accepts
        }
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
