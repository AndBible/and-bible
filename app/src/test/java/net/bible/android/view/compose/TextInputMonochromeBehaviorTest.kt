package net.bible.android.view.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.components.AbTextInputContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Selection must stay readable, not merely become a palette-clean solid rectangle. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class TextInputMonochromeBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightSelectionKeepsGlyphsAndReplacesInitialText() = selection(false)
    @Test fun darkSelectionKeepsGlyphsAndReplacesInitialText() = selection(true)

    @Test fun lightPasswordRetainsProtectedSemantics() = password(false)
    @Test fun darkPasswordRetainsProtectedSemantics() = password(true)

    private fun password(dark: Boolean) {
        compose.setContent {
            AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
                AbTextInputContent(initial = "secret", masked = true, onValueChange = {})
            }
        }
        val node = compose.onNode(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNode()
        assertTrue("masked input must expose Password semantics", node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Password))
        assertTrue("password must not offer Copy", !node.config.contains(androidx.compose.ui.semantics.SemanticsActions.CopyText))
        assertTrue("password must not offer Cut", !node.config.contains(androidx.compose.ui.semantics.SemanticsActions.CutText))
    }

    private fun selection(dark: Boolean) {
        var density = 1f
        var changed = ""
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    AbTextInputContent(initial = "Selected text", onValueChange = { changed = it })
                }
            }
        }
        compose.waitForIdle()
        val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(android.graphics.Canvas(bitmap)) }
        val ink = if (dark) 0xFFFFFF else 0x000000
        val paper = if (dark) 0x000000 else 0xFFFFFF
        var inkPixels = 0
        var paperPixels = 0
        // Inside the initial selection, excluding the field border and surrounding paper.
        for (x in (18 * density).toInt() until (108 * density).toInt()) {
            for (y in (18 * density).toInt() until (38 * density).toInt()) {
                when (bitmap.getPixel(x, y) and 0xFFFFFF) {
                    ink -> inkPixels++
                    paper -> paperPixels++
                }
            }
        }
        assertTrue("selection must have opaque ink background", inkPixels > 500 * density * density)
        assertTrue("selected glyphs must remain paper, not disappear into ink", paperPixels > 50 * density * density)
        compose.onNodeWithText("Selected text").performTextInput("Replacement")
        assertEquals("Replacement", changed)
    }
}
