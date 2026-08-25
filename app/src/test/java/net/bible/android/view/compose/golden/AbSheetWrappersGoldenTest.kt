package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbActionSheetContent
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbChoiceSheetContent
import net.bible.sharedui.components.AbMultiSelectSheetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `…Content` goldens for round 14a's three shared sheet wrappers (spec §4).
 *
 * The WRAPPERS themselves are never captured here: an open `ModalBottomSheet` hangs Roborazzi and
 * takes the whole `:app` suite with it, which `SettingsEditorSheetGuardTest` machine-enforces by
 * name. So these captures prove the bodies — header, bounded scroll region, bottom fade, rows — and
 * NOT the sheet around them; the chrome of an actually-open sheet is device-pass-only coverage
 * (spec §9.1).
 *
 * Every body is wrapped in [SheetSurface] rather than captured on the harness's bare theme
 * background, because `Modifier.abBottomFade` ramps from the SURFACE colour to itself: on any other
 * background the fade would either be invisible or read as a coloured band, and the golden would
 * prove nothing about what the sheet really looks like.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbSheetWrappersGoldenTest {

    /** The same choice list the deleted `AiPrompts_move_to_category_dialog_*` goldens showed:
     *  "(uncategorized)" first, then the one real category, pre-selecting the current one. */
    private val categories = listOf(
        SettingsItem.Choice(value = "", label = "(uncategorized)"),
        SettingsItem.Choice(value = "cat-summary", label = "Summarization"),
    )

    @Test fun choice_matrix() = captureMatrix("AbSheetWrappers", "choice", heightDp = 420) {
        SheetSurface {
            AbChoiceSheetContent(
                title = "Move to category…",
                choices = categories,
                selectedValue = "cat-summary",
                onSelect = {},
                onClose = {},
            )
        }
    }

    /** Replaces `CloudDocuments_syncnow_light.png`: the same three two-line sync rows, the same
     *  pre-checked pattern, now as the sheet body — one mode, because the dialog it replaces had
     *  exactly one. */
    @Test fun multiSelect_light() = captureGolden("AbSheetWrappers", "multiSelect", EDGE_MODE, heightDp = 460) {
        SheetSurface {
            AbMultiSelectSheetContent(
                title = "Sync now",
                options = listOf(
                    "dl" to "Download\n2 documents (8.0 MB)",
                    "ul" to "Upload\n1 document (4.2 MB)",
                    "rm" to "Delete\nnothing to transfer",
                ),
                selectedIds = listOf("dl", "ul"),
                idOf = { it.first },
                labelOf = { it.second },
                confirmText = "OK",
                dismissText = "Cancel",
                onConfirm = {},
                onCancel = {},
                onClose = {},
            )
        }
    }

    /** The export-destination chooser of G2.9/G2.10 — the new action-sheet shape. One mode: the
     *  dialog it replaces had no goldens at all, and this capture exists to pin the shape (title,
     *  message, iconed rows, no confirm row) rather than to re-prove the theme matrix. */
    @Test fun action_light() = captureGolden("AbSheetWrappers", "action", EDGE_MODE, heightDp = 340) {
        SheetSurface {
            AbActionSheetContent(
                title = "Export to",
                message = "Share the file, or save it to this phone?",
                onClose = {},
            ) {
                AbActionSheetRow(
                    label = "Share",
                    onClick = {},
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                )
                AbActionSheetRow(
                    label = "Phone storage",
                    onClick = {},
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                )
            }
        }
    }
}

/** What `ModalBottomSheet` paints behind its content: `BottomSheetDefaults.ContainerColor`, which is
 *  `surfaceContainerLow`. Named here rather than inlined so all three wrappers' captures share it. */
@Composable
internal fun SheetSurface(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { Column { content() } }
}
