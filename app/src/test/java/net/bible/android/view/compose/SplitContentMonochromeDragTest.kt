package net.bible.android.view.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.SplitContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
class SplitContentMonochromeDragTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun horizontalSplitKeepsFullDraggingStroke() = draggingStroke(reverse = false)
    @Test fun verticalSplitKeepsFullDraggingStroke() = draggingStroke(reverse = true)

    private fun draggingStroke(reverse: Boolean) {
        val windows = listOf("A", "B").map { id ->
            WindowSnapshot(id, WindowStateValue.VISIBLE, 1f, true, true, false, 0, false)
        }
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.MONOCHROME) {
                Box(Modifier.size(240.dp, 160.dp).testTag("split")) {
                    SplitContent(
                        WindowLayoutState(windows, "A", null, reverse, true),
                        onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                        pane = {}, paneBackground = { Color.Magenta },
                    )
                }
            }
        }
        compose.waitForIdle()
        val split = compose.onNodeWithTag("split")
        split.performTouchInput {
            down(center)
            moveBy(if (reverse) Offset(0f, 30f * density) else Offset(30f * density, 0f))
        }
        compose.waitForIdle()
        val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(android.graphics.Canvas(bitmap)) }
        val width = (240 * density).toInt()
        val height = (160 * density).toInt()
        val inkPixels = if (reverse) {
            (0 until height).count { Color(bitmap.getPixel(width / 2, it)) == Color.Black }
        } else {
            (0 until width).count { Color(bitmap.getPixel(it, height / 2)) == Color.Black }
        }
        assertEquals("The opaque sibling pane must not cover half the doubled separator", (8 * density).toInt(), inkPixels)
        split.performTouchInput { up() }
    }
}
