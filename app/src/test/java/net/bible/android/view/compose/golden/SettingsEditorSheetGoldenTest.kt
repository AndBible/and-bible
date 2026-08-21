package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbListChoiceContent
import net.bible.sharedui.components.AbMultiSelectContent
import net.bible.sharedui.components.AbTextInputContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three generic settings editor page bodies.
 *
 * Captured **directly**, never inside `SettingsEditorSheet`: forcing a `ModalBottomSheet` open in a
 * capture hangs Roborazzi (see `SearchSheetGoldenTest`), and it is the page body — not the sheet
 * chrome — that needs proving. The wrapping `Column` stands in for the sheet's own `Column`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SettingsEditorSheetGoldenTest {

    private val choices = listOf(
        SettingsItem.Choice("sans", "Sans serif"),
        SettingsItem.Choice("serif", "Serif"),
        SettingsItem.Choice("mono", "Monospace"),
    )

    @Test fun listChoice_matrix() =
        captureMatrix("SettingsEditorSheet", "listChoice") {
            Column {
                AbListChoiceContent(choices = choices, selectedValue = "serif", onSelect = {})
            }
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun listChoice_rtl() =
        captureRtl("SettingsEditorSheet", "listChoice") {
            Column {
                AbListChoiceContent(choices = choices, selectedValue = "serif", onSelect = {})
            }
        }

    @Test fun textInput_matrix() =
        captureMatrix("SettingsEditorSheet", "textInput") {
            Column { AbTextInputContent(initial = "https://example.com", onValueChange = {}) }
        }

    @Test fun multiSelect_matrix() =
        captureMatrix("SettingsEditorSheet", "multiSelect") {
            Column {
                AbMultiSelectContent(
                    options = choices,
                    selectedIds = listOf("sans", "mono"),
                    idOf = { it.value },
                    labelOf = { it.label },
                    onCheckedChange = {},
                    selectAllText = "Select all",
                    selectNoneText = "Select none",
                )
            }
        }
}
