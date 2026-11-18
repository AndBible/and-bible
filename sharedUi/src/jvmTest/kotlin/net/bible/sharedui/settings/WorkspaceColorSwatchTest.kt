package net.bible.sharedui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import net.bible.sharedcore.settings.ColorSettingsUiState
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.theme.toGrayscaleArgb
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.DefaultThemeProposal
import net.bible.sharedui.theme.WORKSPACE_DEFAULT_RING_WIDTH
import net.bible.sharedui.theme.abColorScheme
import net.bible.sharedui.theme.monoInk
import net.bible.sharedui.theme.monoPaper
import net.bible.sharedui.theme.ringFraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceColorSwatchTest {
    @Test
    fun unsetLightSwatchShowsDefaultThemePrimary() = assertUnsetSwatch(dark = false)

    @Test
    fun unsetDarkSwatchShowsDefaultThemePrimary() = assertUnsetSwatch(dark = true)

    @Test
    fun detachedUnsetWorkspaceDoesNotBorrowActiveWorkspaceColor() =
        assertUnsetSwatch(dark = false, activeSeed = 0xFFFF8000.toInt())

    @Test
    fun chosenWorkspaceSwatchKeepsItsLiteralColor() =
        assertUnsetSwatch(dark = false, workspaceColor = 0xFF8B5A2B.toInt())

    // #4149: an unset colour shows the toolbar colour of a workspace without its own colour (the
    // unset scheme's primaryContainer; paper in the ink modes) with a dashed ring, never #444444.
    @Test
    fun einkDefaultSwatchShowsPaperWithInkRing() =
        assertUnsetSwatch(dark = false, mode = DisplayColorMode.COLOR_EINK)

    @Test
    fun monochromeDarkDefaultSwatchShowsPaperWithInkRing() =
        assertUnsetSwatch(dark = true, mode = DisplayColorMode.MONOCHROME)

    @Test
    fun bwDefaultSwatchShowsGreyThemeContainer() =
        assertUnsetSwatch(dark = false, mode = DisplayColorMode.BW)

    @Test
    fun einkChosenSwatchKeepsItsLiteralColor() =
        assertUnsetSwatch(dark = false, workspaceColor = 0xFF8B5A2B.toInt(), mode = DisplayColorMode.COLOR_EINK)

    @Test
    fun monochromeChosenSwatchIsGrey() =
        assertUnsetSwatch(dark = false, workspaceColor = 0xFF8B5A2B.toInt(), mode = DisplayColorMode.MONOCHROME)

    @Test
    fun bwChosenSwatchIsGrey() =
        assertUnsetSwatch(dark = true, workspaceColor = 0xFF8B5A2B.toInt(), mode = DisplayColorMode.BW)

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
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
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
        }
        // Screen readers get in words what the dashed ring shows.
        onNodeWithText("Workspace color").assert(
            if (workspaceColor == DEFAULT_WORKSPACE_COLOR_ARGB) {
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Default")
            } else {
                SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)
            },
        )
        val row = onNodeWithText("Workspace color").captureToImage()
        // Sample inside the circle, away from its ring and the adjacent label.
        val pixels = row.toPixelMap()
        val radius = row.height / 4
        // The built-in default theme ("Ink on paper"), never the stock Material violet.
        val default = DefaultThemeProposal.DEFAULT.scheme(dark)!!
        val unset = workspaceColor == DEFAULT_WORKSPACE_COLOR_ARGB
        val expected = if (!unset) when (mode) {
            DisplayColorMode.NORMAL, DisplayColorMode.COLOR_EINK -> Color(workspaceColor)
            DisplayColorMode.MONOCHROME, DisplayColorMode.BW -> Color(toGrayscaleArgb(workspaceColor))
        } else when (mode) {
            DisplayColorMode.NORMAL -> default.primaryContainer
            DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoPaper(dark)
            DisplayColorMode.BW -> abColorScheme(null, dark, mode).primaryContainer
        }
        // The swatch is 32dp, starting at the row's start edge.
        val c = 16f * DENSITY
        val ring = WORKSPACE_DEFAULT_RING_WIDTH.value * DENSITY
        val ringColor = when (mode) {
            DisplayColorMode.NORMAL -> default.outline
            DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK -> monoInk(dark)
            DisplayColorMode.BW -> abColorScheme(null, dark, mode).outline
        }
        val fraction = pixels.ringFraction(c, row.height / 2f, c - ring / 2, ringColor)
        if (unset) assertTrue(fraction in 0.25f..0.85f, "dashed ring fraction $fraction")
        else assertTrue(fraction < 0.25f || mode.isInkBase, "a chosen colour has no dashed ring: $fraction")
        assertEquals(expected, pixels[radius, row.height / 2])
    }

    private companion object {
        const val DENSITY = 3f
    }
}
