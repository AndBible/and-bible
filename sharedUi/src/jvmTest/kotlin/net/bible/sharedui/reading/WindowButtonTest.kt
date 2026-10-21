package net.bible.sharedui.reading

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.theme.AbTheme

/** The pointer handler is installed once; it must still call the lambdas of the latest composition. */
@OptIn(ExperimentalTestApi::class)
class WindowButtonTest {
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
