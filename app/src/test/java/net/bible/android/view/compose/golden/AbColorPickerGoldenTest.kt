package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbColorPicker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbColorPickerGoldenTest {
    @Test fun colorpicker_primary() {
        captureMatrix("AbColorPicker", "primary", heightDp = 320) {
            AbColorPicker(color = AbColor.palette.first(), onColorChange = {})
        }
    }
}
