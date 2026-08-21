package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 *
 * [listChoiceLong_matrix] additionally wraps the list-choice body in the same
 * `Box(Modifier.heightIn(max = 400.dp))` ancestor `GenericSettingsEditorSheet` uses in production
 * (fix round 1 finding: a bound passed via `AbListChoiceContent`'s own `modifier` parameter lands
 * inside its `verticalScroll` and does not work — see that composable's KDoc). Honest scope of what
 * a static capture can and cannot prove here: it shows the `Box` clamps to 400.dp and that rows past
 * the clamp are not drawn (i.e. the bound is applied and nothing overflows the sheet) — it CANNOT
 * prove the list is actually scrollable to reach those clipped rows, since a screenshot has no
 * gesture. That half (does dragging the list reveal the rest) needs the on-device checklist.
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

    // A realistic long list (e.g. font families) at the SAME Box-wrapped composition
    // GenericSettingsEditorSheet renders in production -- see the class kdoc for what this test
    // proves and what it does not.
    private val longChoices = (1..20).map { SettingsItem.Choice("font$it", "Font family $it") }

    @Test fun listChoiceLong_matrix() =
        captureMatrix("SettingsEditorSheet", "listChoiceLong") {
            Column {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    AbListChoiceContent(choices = longChoices, selectedValue = "font1", onSelect = {})
                }
            }
        }
}
