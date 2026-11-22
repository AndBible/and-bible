package net.bible.sharedui.navigation

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.theme.AbTheme
import net.bible.service.common.DisplayColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** #4186: the current key row must be identifiable without colour in ink/paper modes. */
@OptIn(ExperimentalTestApi::class)
class KeyListBodyCurrentRowTest {
    private val rows = listOf(KeyRow("0", "Alpha"), KeyRow("1", "Beta"), KeyRow("2", "Gamma"))

    private fun androidx.compose.ui.test.ComposeUiTest.show(mode: DisplayColorMode) = setContent {
        AbTheme(darkTheme = false, colorMode = mode, disableAnimations = true) {
            KeyListBody(rows = rows, currentKeyId = "1", onSelect = {})
        }
    }

    @Test
    fun currentRowReportsSelectedSemantics() = runComposeUiTest {
        show(DisplayColorMode.COLOR_EINK)
        waitForIdle()
        onNodeWithText("Beta").assertIsSelected()
        onNodeWithText("Alpha").assertIsNotSelected()
    }

    @Test
    fun einkCurrentRowHasInkOutlineAndOthersDoNot() = runComposeUiTest {
        show(DisplayColorMode.COLOR_EINK)
        waitForIdle()
        fun edgePixel(text: String): androidx.compose.ui.graphics.Color {
            val img = onNodeWithText(text).captureToImage().toPixelMap()
            return img[1, img.height / 2]
        }
        val current = edgePixel("Beta")
        val other = edgePixel("Alpha")
        assertNotEquals(other, current, "current row must differ from others in ink mode")
        assertTrue(current.red < 0.3f && current.green < 0.3f && current.blue < 0.3f, "outline is dark ink: $current")
        assertEquals(current.red, current.green, 0.01f, "grayscale")
        assertEquals(current.green, current.blue, 0.01f, "grayscale")
    }
}
