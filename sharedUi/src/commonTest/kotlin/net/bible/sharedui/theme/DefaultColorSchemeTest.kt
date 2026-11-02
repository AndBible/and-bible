package net.bible.sharedui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DefaultColorSchemeTest {
    @Test
    fun unsetWorkspaceUsesEveryStockRoleRegardlessOfPaletteStyle() {
        for (dark in listOf(false, true)) {
            val stock = if (dark) darkColorScheme() else lightColorScheme()
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                for (style in ThemePaletteStyle.entries) {
                    // ColorScheme has identity equality; toString enumerates all its roles.
                    assertEquals(stock.toString(), abColorScheme(seed, dark, DisplayColorMode.NORMAL, style).toString(),
                        "dark=$dark seed=$seed style=$style")
                }
            }
        }
    }

    @Test
    fun chosenWorkspaceStillUsesTheSelectedPalette() {
        val seed = Color(0xFF1565C0)
        val paletteStyles = mapOf(
            ThemePaletteStyle.TONAL_SPOT_GREY to PaletteStyle.TonalSpot,
            ThemePaletteStyle.FIDELITY to PaletteStyle.Fidelity,
            ThemePaletteStyle.TONAL_SPOT to PaletteStyle.TonalSpot,
            ThemePaletteStyle.CONTENT to PaletteStyle.Content,
            ThemePaletteStyle.NEUTRAL to PaletteStyle.Neutral,
            ThemePaletteStyle.MONOCHROME to PaletteStyle.Monochrome,
            ThemePaletteStyle.VIBRANT to PaletteStyle.Vibrant,
            ThemePaletteStyle.EXPRESSIVE to PaletteStyle.Expressive,
            ThemePaletteStyle.RAINBOW to PaletteStyle.Rainbow,
            ThemePaletteStyle.FRUIT_SALAD to PaletteStyle.FruitSalad,
        )
        for (dark in listOf(false, true)) {
            for ((style, paletteStyle) in paletteStyles) {
                val expected = dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false,
                    style = paletteStyle)
                assertEquals(expected.toString(), abColorScheme(seed.toArgb(), dark, DisplayColorMode.NORMAL, style).toString(),
                    "dark=$dark style=$style")
            }
            assertNotEquals(
                abColorScheme(seed.toArgb(), dark, DisplayColorMode.NORMAL, ThemePaletteStyle.FIDELITY).toString(),
                abColorScheme(seed.toArgb(), dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT).toString(),
            )
        }
    }

    @Test
    fun einkDefaultsKeepTheirExistingGreyRoles() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                for (style in ThemePaletteStyle.entries) {
                    for (mode in listOf(DisplayColorMode.BW, DisplayColorMode.COLOR_EINK)) {
                        val scheme = abColorScheme(seed, dark, mode, style)
                        assertEquals(if (dark) 0xFFC9C9C9.toInt() else 0xFF606060.toInt(), scheme.primary.toArgb())
                        assertEquals(if (dark) 0xFF131313.toInt() else 0xFFFAFAFA.toInt(), scheme.surface.toArgb())
                    }
                    val mono = abColorScheme(seed, dark, DisplayColorMode.MONOCHROME, style)
                    assertEquals(if (dark) Color.White else Color.Black, mono.primary)
                    assertEquals(if (dark) Color.Black else Color.White, mono.surface)
                    assertEquals(Color.Transparent, mono.scrim)
                }
            }
        }
    }
}
