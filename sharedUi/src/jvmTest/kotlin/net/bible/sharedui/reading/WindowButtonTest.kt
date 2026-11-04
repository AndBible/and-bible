package net.bible.sharedui.reading

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.theme.AbTheme

/** Covers fixed-size rail layout and pointer handlers across recompositions. */
@OptIn(ExperimentalTestApi::class)
class WindowButtonTest {
    @Test
    fun bothRailLabelsFitTheirMeasuredLineBoxesAtLargeFontScale() = assertRailLabelsFit(1f, 2f)

    @Test
    fun railLabelsFitAtWindowsDisplayScales() {
        for (density in listOf(1f, 1.25f, 1.5f)) assertRailLabelsFit(density, 1f)
    }

    private fun assertRailLabelsFit(density: Float, fontScale: Float) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = fontScale)) {
                AbTheme {
                    WindowButton(
                        label = "KJV", topLabel = "Jo 3:16", isActive = true, isMinimised = false,
                        isPinned = false, isLinks = false, syncGroup = 1, mode = WindowButtonMode.Rail,
                        onClick = {}, onLongPress = {},
                    )
                }
            }
        }
        for (text in listOf("Jo 3:16", "KJV")) {
            val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight, "$text must not be vertically truncated: $layout")
            assertTrue(node.boundsInRoot.height >= layout.multiParagraph.height,
                "$text needs ${layout.multiParagraph.height}px but gets ${node.boundsInRoot.height}px")
            assertTrue(node.boundsInRoot.top >= 16f * density - 1f, "$text must stay below the badge row")
            assertTrue(node.boundsInRoot.bottom <= 39f * density + 1f, "$text must stay above the bottom inset")
        }
        val reference = onNodeWithText("Jo 3:16", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val document = onNodeWithText("KJV", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(reference.bottom <= document.top, "The reference and document lines must not overlap")
    }

    @Test
    fun gestureUsesLatestLambdas() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var generation by mutableStateOf(1)
        setContent {
            AbTheme {
                val g = generation
                WindowButton(
                    label = "KJV", isActive = true, isMinimised = false, isPinned = false, isLinks = false, syncGroup = 0,
                    mode = WindowButtonMode.Rail, onClick = { calls += "click$g" }, onLongPress = { calls += "long$g" },
                )
            }
        }
        val tab = onNodeWithText("KJV")
        tab.performTouchInput { click() }
        tab.performTouchInput { longClick() }
        generation = 2
        waitForIdle()
        tab.performTouchInput { click() }
        tab.performTouchInput { longClick() }
        assertEquals(listOf("click1", "long1", "click2", "long2"), calls)
    }
}
