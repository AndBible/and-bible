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

    /** Enough choices that the list overflows [net.bible.sharedui.components.AbSheetContentMaxHeight]
     *  (400dp) — [choice_matrix]'s two rows never do, so no existing golden proves the bottom fade
     *  actually renders inside a sheet body (final-review fix wave, M4/M5). */
    private val overflowChoices = (1..20).map { SettingsItem.Choice(value = "c$it", label = "Choice $it") }

    /**
     * Proves the fade is genuinely WIRED to the hoisted scroll state, not merely present in the
     * modifier chain: with 20 rows the content overflows the 400dp bound, `scrollState.canScrollForward`
     * is true at rest, and `Modifier.abBottomFade` (read in the draw phase off that same lambda) must
     * paint. `choice_matrix`/`multiSelect_light`/`action_light` above and every other `…Content`
     * golden in this port fit inside their bound, so `canScrollForward` is false in all of them and
     * the fade never renders anywhere else in the suite — a refactor that let
     * `AbListChoiceContent` default its own `rememberScrollState()` instead of receiving the one
     * `AbChoiceSheetContent` hoists would silently disconnect the fade from real scroll position
     * (`canScrollForward` permanently false, the affordance gone) with every existing golden still
     * byte-identical. This capture is the one that would catch it: the fade would vanish here.
     *
     * One mode only ([EDGE_MODE]) — the four-mode matrix is already proven by [choice_matrix]; what
     * this test adds is the overflow case, not new theme coverage. `heightDp = 480` is header
     * (`AbSheetHeader`, `heightIn(min = 48.dp)`) + the 400dp bound + the body's own
     * `padding(bottom = 16.dp)`, plus headroom so the capture shows the fade sitting INSIDE the
     * bound with background visible below it, proving the bound (and the fade with it) does not
     * silently grow past its cap.
     *
     * Deliberately NOT [AbChoiceSheet] itself — an open `ModalBottomSheet` hangs Roborazzi and the
     * whole `:app` suite with it (`SettingsEditorSheetGuardTest`).
     */
    @Test fun choiceOverflow() = captureGolden("AbSheetWrappers", "choiceOverflow", EDGE_MODE, heightDp = 480) {
        SheetSurface {
            AbChoiceSheetContent(
                title = "Move to category…",
                choices = overflowChoices,
                selectedValue = "c1",
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
