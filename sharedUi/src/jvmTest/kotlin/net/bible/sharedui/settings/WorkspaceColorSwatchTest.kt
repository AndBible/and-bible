package net.bible.sharedui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import net.bible.sharedcore.settings.ColorSettingsUiState
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorkspaceColorSwatchTest {
    @Test
    fun unsetLightSwatchShowsStockPrimary() = assertUnsetSwatch(dark = false)

    @Test
    fun unsetDarkSwatchShowsStockPrimary() = assertUnsetSwatch(dark = true)

    @Test
    fun detachedUnsetWorkspaceDoesNotBorrowActiveWorkspaceColor() =
        assertUnsetSwatch(dark = false, activeSeed = 0xFFFF8000.toInt())

    @Test
    fun chosenWorkspaceSwatchKeepsItsLiteralColor() =
        assertUnsetSwatch(dark = false, workspaceColor = 0xFF8B5A2B.toInt())

    @Test
    fun einkDefaultSwatchStaysGrey() =
        assertUnsetSwatch(dark = false, mode = DisplayColorMode.COLOR_EINK)

    private fun assertUnsetSwatch(
        dark: Boolean,
        activeSeed: Int? = null,
        workspaceColor: Int = DEFAULT_WORKSPACE_COLOR_ARGB,
        mode: DisplayColorMode = DisplayColorMode.NORMAL,
    ) = runComposeUiTest {
        val state = ColorSettingsUiState(
            colors = ColorsSnapshot(
                title = "Colors", dayTextColor = -16777216, dayBackground = -1, dayNoise = 0,
                nightTextColor = -1, nightBackground = -16777216, nightNoise = 0,
                workspaceColor = workspaceColor, workspaceColorVisible = true,
                dayBackgroundImageInitials = null, dayBackgroundImageName = "None", dayBackgroundImageOpacity = 100,
                nightBackgroundImageInitials = null, nightBackgroundImageName = "None", nightBackgroundImageOpacity = 100,
                inheritedFrom = InheritedFrom.NONE,
            ),
            backgroundOptions = emptyList(),
        )
        setContent {
            AbTheme(darkTheme = dark, seedArgb = activeSeed, colorMode = mode, disableAnimations = true) {
                Box(Modifier.width(320.dp)) {
                    ColorSettingsContent(
                        state = state, labels = ColorSettingsLabels.forTest(), onColorFieldClick = {},
                        onWorkspaceColorClick = {}, onNoiseChange = { _, _ -> },
                        onOpacityChange = { _, _ -> }, onChangeBackgroundImage = {},
                    )
                }
            }
        }
        val row = onNodeWithText("Workspace color").captureToImage()
        // Sample inside the circle, away from its border and the adjacent label (density-independent).
        val pixels = row.toPixelMap()
        val radius = row.height / 4
        val stock = if (dark) darkColorScheme() else lightColorScheme()
        val expected = if (mode == DisplayColorMode.NORMAL && workspaceColor == DEFAULT_WORKSPACE_COLOR_ARGB)
            stock.primary else Color(workspaceColor)
        assertEquals(expected, pixels[radius, row.height / 2])
    }
}
