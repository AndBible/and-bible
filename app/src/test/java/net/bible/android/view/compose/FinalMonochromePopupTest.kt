package net.bible.android.view.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures the actual popup root, never Roborazzi's multi-window unbounded idle path. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class FinalMonochromePopupTest {
    @get:Rule val rule = createComposeRule()

    @Test(timeout = 60000) fun openedDropdownHasOutlineBothThemesAndSelects() {
        val dark = mutableStateOf(false)
        val selected = mutableStateOf("First")
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
            net.bible.sharedui.components.AbDropdownField("Category", selected.value, listOf("First", "Second"), { it }, { selected.value = it })
        } } }
        for (theme in listOf(false, true)) {
            rule.runOnIdle { dark.value = theme; selected.value = "First" }
            rule.onNodeWithText("First").performClick()
            rule.onNodeWithText("Second").assertIsDisplayed()
            assertPopupOutline(theme)
            rule.onNodeWithText("Second").performClick()
            rule.onNode(isPopup()).assertDoesNotExist()
            org.junit.Assert.assertEquals("Second", selected.value)
        }
    }

    private fun assertPopupOutline(theme: Boolean) {
        rule.onNode(isPopup()).assertExists()
        val popupView = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val bitmap = android.graphics.Bitmap.createBitmap(popupView.width, popupView.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnIdle { popupView.draw(android.graphics.Canvas(bitmap)) }
        val pixels = bitmap.asImageBitmap().toPixelMap()
        val ink = if (theme) 1f else 0f
        assertTrue("Real popup top outline is opaque ink, dark=$theme",
            (0 until pixels.height.coerceAtMost(5)).any { y ->
                (12 until pixels.width - 12).all { x -> pixels[x, y].let { it.red == ink && it.green == ink && it.blue == ink && it.alpha == 1f } }
            })
    }

    @Test(timeout = 60000) fun openedOverflowHasOutlineBothThemesAndDismisses() {
        val dark = mutableStateOf(false)
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
            Box { AbOverflowMenu { close -> DropdownMenuItem(text = { Text("Actual popup option") }, onClick = close) } }
        } } }
        for (theme in listOf(false, true)) {
            rule.runOnIdle { dark.value = theme }
            rule.onNodeWithContentDescription("Menu").performClick()
            rule.onNodeWithText("Actual popup option").assertIsDisplayed()
            assertPopupOutline(theme)
            rule.onNodeWithText("Actual popup option").performClick()
            rule.onNodeWithText("Actual popup option").assertDoesNotExist()
        }
    }
}
