package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.components.ColorPickerCustomPage
import net.bible.sharedui.components.ColorPickerPresetsPage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The picker's two pages. The DIALOG is deliberately never captured: Roborazzi hangs on an open
 *  dialog/popup (standing rule of this port), and the pages are where the pixels are. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbColorPickerGoldenTest {

    // A non-Material colour on purpose: it is unshifted to the front of the grid (so the
    // "current colour" swatch is visible and checked) and gives a full shade row underneath.
    private val seed = 0xFF123456.toInt()

    @Test fun colorpicker_presets() =
        captureMatrix("AbColorPicker", "presets", heightDp = 620) {
            ColorPickerPresetsPage(initialColor = seed, color = seed, onColorChange = {})
        }

    // The working colour differs from `initialColor` on purpose: it is the only way the old->new
    // preview pair shows two different colours in the image.
    @Test fun colorpicker_custom() =
        captureMatrix("AbColorPicker", "custom", heightDp = 480) {
            ColorPickerCustomPage(initialColor = seed, color = 0xFF2196F3.toInt(), onColorChange = {})
        }
}
