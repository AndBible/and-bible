package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorSettingsUiState
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedui.components.AbLoadingOverlay
import net.bible.sharedui.settings.BackgroundImageChooserContent
import net.bible.sharedui.settings.BackgroundImageChooserLabels
import net.bible.sharedui.settings.ColorSettingsContent
import net.bible.sharedui.settings.ColorSettingsLabels
import net.bible.sharedui.settings.ColorSettingsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [ColorSettingsScreen]. Unlike [SyncSettingsGoldenTest], no controller/service is
 * needed here — [ColorSettingsUiState] is built directly from a hand-written [ColorsSnapshot],
 * matching how simple this screen's state shape is.
 *
 * T8 additionally goldens the two sheet-page bodies extracted out of this screen and out of
 * [net.bible.sharedui.settings.BackgroundImageChooserScreen] — [ColorSettingsContent] and
 * [BackgroundImageChooserContent] — captured directly in a plain `Column`, never inside
 * `net.bible.sharedui.settings.ColorSettingsEditorSheet`'s `ModalBottomSheet` (forcing one open in
 * a capture hangs Roborazzi and the whole `:app` suite with it — see `SettingsEditorSheetGoldenTest`).
 * [backgroundImageContent_long_matrix] additionally wraps the content in the SAME
 * `Box(Modifier.heightIn(max = 400.dp))` ancestor `ColorSettingsEditorSheet` uses in production, the
 * same honest-scope caveat as `SettingsEditorSheetGoldenTest.listChoiceLong_matrix` applies: this
 * proves the bound clips correctly, not that the grid is actually scrollable past it.
 *
 * T9 adds [backgroundImageContent_loading_edge], the same Box-wrapped composition PLUS an
 * [AbLoadingOverlay] sibling — reproducing exactly what `ColorSettingsEditorSheet`'s `BackgroundImage`
 * page now renders while `ColorSettingsUiState.loading` is true (Task 8 left that state deliberately
 * unhandled there; T9 owns the import path and wires it). Still captured directly, not through the
 * sheet's `ModalBottomSheet`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ColorSettingsScreenGoldenTest {

    private fun uiState(windowScope: Boolean = false, withImage: Boolean = false) = ColorSettingsUiState(
        colors = ColorsSnapshot(
            title = if (windowScope) "Window colours" else "Workspace colours",
            dayTextColor = -16777216, dayBackground = -1, dayNoise = 0,
            nightTextColor = -1, nightBackground = -16777216, nightNoise = 20,
            workspaceColor = -12303292, workspaceColorVisible = !windowScope,
            dayBackgroundImageInitials = if (withImage) "BGIMG_hills" else null,
            dayBackgroundImageName = if (withImage) "hills" else "None", dayBackgroundImageOpacity = if (withImage) 60 else 100,
            nightBackgroundImageInitials = null, nightBackgroundImageName = "None", nightBackgroundImageOpacity = 100,
            inheritedFrom = InheritedFrom.NONE,
        ),
        backgroundOptions = emptyList(),
    )

    private fun screen(s: ColorSettingsUiState): @Composable () -> Unit = {
        // Named, not positional: final fix wave, Fix 4 added three String params (the reset
        // confirm's message/confirm/cancel text) between onReset and onColorChange -- named args
        // survive that insertion; the old positional call would have silently mismatched types.
        ColorSettingsScreen(
            state = s,
            labels = ColorSettingsLabels.forTest(),
            onUp = {},
            onReset = {},
            resetConfirmMessage = "Reset colours?",
            confirmLabel = "OK",
            cancelLabel = "Cancel",
            onColorChange = { _, _ -> },
            onNoiseChange = { _, _ -> },
            onWorkspaceColorChange = {},
            onWorkspaceColorReset = {},
            onOpacityChange = { _, _ -> },
            onChangeBackgroundImage = {},
        )
    }

    @Test fun workspace_matrix() = captureMatrix("ColorSettings", "workspace", heightDp = 1600, content = screen(uiState()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun workspace_rtl() = captureRtl("ColorSettings", "workspace", heightDp = 1600, content = screen(uiState()))

    @Test fun windowscope_edge() = captureGolden("ColorSettings", "windowscope", EDGE_MODE, heightDp = 1600, content = screen(uiState(windowScope = true)))

    @Test fun withimage_edge() = captureGolden("ColorSettings", "withimage", EDGE_MODE, heightDp = 1600, content = screen(uiState(withImage = true)))

    // --- T8: sheet-page bodies (ColorSettingsEditorSheet's Colors / BackgroundImage pages) ---

    private fun colorSettingsContent(s: ColorSettingsUiState): @Composable () -> Unit = {
        Column {
            ColorSettingsContent(
                state = s,
                labels = ColorSettingsLabels.forTest(),
                onColorFieldClick = {},
                onWorkspaceColorClick = {},
                onNoiseChange = { _, _ -> },
                onOpacityChange = { _, _ -> },
                onChangeBackgroundImage = {},
            )
        }
    }

    @Test fun colorSettingsContent_matrix() =
        captureMatrix("ColorSettingsContent", "workspace", heightDp = 1600, content = colorSettingsContent(uiState()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun colorSettingsContent_rtl() =
        captureRtl("ColorSettingsContent", "workspace", heightDp = 1600, content = colorSettingsContent(uiState()))

    private val chooserOpts = listOf(
        BackgroundImageOption("BGIMG_hills", "hills", "BGIMG_hills"),
        BackgroundImageOption("BGIMG_sky", "sky", "BGIMG_sky"),
    )

    private fun backgroundImageChooserContent(
        options: List<BackgroundImageOption> = chooserOpts,
        importVisible: Boolean = true,
    ): @Composable () -> Unit = {
        Column {
            BackgroundImageChooserContent(
                options = options,
                labels = BackgroundImageChooserLabels.forTest(),
                thumbnailFor = { null },
                importVisible = importVisible,
                onSelect = {},
                onImport = {},
                onRequestDelete = {},
            )
        }
    }

    @Test fun backgroundImageChooserContent_matrix() =
        captureMatrix("BackgroundImageChooserContent", "populated", heightDp = 1200, content = backgroundImageChooserContent())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun backgroundImageChooserContent_rtl() =
        captureRtl("BackgroundImageChooserContent", "populated", heightDp = 1200, content = backgroundImageChooserContent())

    @Test fun backgroundImageChooserContent_empty_edge() =
        captureGolden(
            "BackgroundImageChooserContent", "empty", EDGE_MODE, heightDp = 1200,
            content = backgroundImageChooserContent(options = emptyList()),
        )

    @Test fun backgroundImageChooserContent_noimport_edge() =
        captureGolden(
            "BackgroundImageChooserContent", "noimport", EDGE_MODE, heightDp = 1200,
            content = backgroundImageChooserContent(importVisible = false),
        )

    // A realistic long list at the SAME Box-wrapped composition ColorSettingsEditorSheet renders in
    // production for its BackgroundImage page — see the class kdoc for what this test proves and
    // what it does not.
    private val longChooserOpts = (1..12).map { BackgroundImageOption("BGIMG_img$it", "image $it", "BGIMG_img$it") }

    @Test fun backgroundImageContent_long_matrix() =
        captureMatrix("BackgroundImageChooserContent", "long", heightDp = 1200) {
            Column {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    BackgroundImageChooserContent(
                        options = longChooserOpts,
                        labels = BackgroundImageChooserLabels.forTest(),
                        thumbnailFor = { null },
                        importVisible = true,
                        onSelect = {},
                        onImport = {},
                        onRequestDelete = {},
                    )
                }
            }
        }

    // T9: ColorSettingsEditorSheet's BackgroundImage page while ColorSettingsUiState.loading is true —
    // same Box(heightIn(max = 400.dp)) ancestor as production, plus the AbLoadingOverlay the sheet now
    // renders as a sibling inside it (mirroring BackgroundImageChooserScreen's own overlay). Captured
    // directly, never through ColorSettingsEditorSheet's ModalBottomSheet (see the class kdoc).
    @Test fun backgroundImageContent_loading_edge() =
        captureGolden(
            "BackgroundImageChooserContent", "loading", EDGE_MODE, heightDp = 1200,
        ) {
            Column {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    BackgroundImageChooserContent(
                        options = chooserOpts,
                        labels = BackgroundImageChooserLabels.forTest(),
                        thumbnailFor = { null },
                        importVisible = true,
                        onSelect = {},
                        onImport = {},
                        onRequestDelete = {},
                    )
                    AbLoadingOverlay(BackgroundImageChooserLabels.forTest().importing)
                }
            }
        }
}
