package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.ColorSettingsUiState
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
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
        ColorSettingsScreen(s, ColorSettingsLabels.forTest(), {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {})
    }

    @Test fun workspace_matrix() = captureMatrix("ColorSettings", "workspace", heightDp = 1600, content = screen(uiState()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun workspace_rtl() = captureRtl("ColorSettings", "workspace", heightDp = 1600, content = screen(uiState()))

    @Test fun windowscope_edge() = captureGolden("ColorSettings", "windowscope", EDGE_MODE, heightDp = 1600, content = screen(uiState(windowScope = true)))

    @Test fun withimage_edge() = captureGolden("ColorSettings", "withimage", EDGE_MODE, heightDp = 1600, content = screen(uiState(withImage = true)))
}
