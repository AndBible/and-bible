package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.components.AbSwitchRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SettingsRowsGoldenTest {
    private val rows = @androidx.compose.runtime.Composable {
        Column {
            AbSwitchRow(label = "Speak titles", checked = true, onCheckedChange = {})
            AbSwitchRow(label = "Auto-bookmark", checked = false, onCheckedChange = {}, summary = "Create a bookmark automatically")
            AbSliderRow(label = "Font size", value = 150, onValueChange = {}, valueRange = 0f..300f, valueLabel = "150 %", iconKey = "font_size_multiplier")
        }
    }

    @Test fun rows_primary() = captureMatrix("SettingsRows", "rows", content = rows)

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun rows_primary_rtl() = captureRtl("SettingsRows", "rows", content = rows)
}
