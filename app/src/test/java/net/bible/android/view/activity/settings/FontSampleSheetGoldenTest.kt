package net.bible.android.view.activity.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import net.bible.android.TEST_SDK
import net.bible.android.view.compose.golden.captureMatrix
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.settings.NumericTextSettingContent
import net.bible.sharedui.settings.SheetResetConfirmRow
import net.bible.sharedui.strings.LocalStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capture the real sheet body, not ModalBottomSheet (which cannot settle in Roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class FontSampleSheetGoldenTest {
    @Test fun fontSampleSheet_matrix() = captureMatrix("TextDisplaySettings", "fontSampleSheet") {
        val strings = LocalStrings.current
        Column {
            Text(strings.fontSizeTitle, style = MaterialTheme.typography.titleMedium)
            NumericTextSettingContent(
                type = TextSettingType.FONTSIZE,
                numeric = TextSettingRowValue.Numeric(24, 1, 60, "24 pt"),
                fontSize = 24,
                lineSpacing = 16,
                font = SettingsItem.ChoiceFont.Family("serif"),
                onValueChange = {},
            )
            SheetResetConfirmRow(
                resetLabel = strings.resetGeneric,
                confirmLabel = strings.okay,
                cancelLabel = strings.cancel,
                onReset = {}, onConfirm = {}, onCancel = {},
            )
        }
    }
}
