package net.bible.sharedui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class DefaultColorSchemeTest {
    @Test
    fun unsetWorkspaceUsesEveryStockRole() {
        for (dark in listOf(false, true)) {
            val stock = if (dark) darkColorScheme() else lightColorScheme()
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                // ColorScheme has identity equality; toString enumerates all its roles.
                assertEquals(stock.toString(), abColorScheme(seed, dark, DisplayColorMode.NORMAL).toString(),
                    "dark=$dark seed=$seed")
            }
        }
    }

    @Test
    fun saturatedWorkspaceKeepsEveryTonalSpotRole() {
        val seed = Color(0xFF1565C0)
        for (dark in listOf(false, true)) {
            val expected = dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false,
                style = PaletteStyle.TonalSpot)
            assertEquals(expected.toString(), abColorScheme(seed.toArgb(), dark, DisplayColorMode.NORMAL).toString())
        }
    }

    @Test
    fun fixedPaletteKeepsMutedSeedsMutedInsteadOfUsingPlainTonalSpot() {
        fun spread(color: Color): Int {
            val argb = color.toArgb()
            val channels = listOf((argb ushr 16) and 255, (argb ushr 8) and 255, argb and 255)
            return channels.max() - channels.min()
        }
        for (dark in listOf(false, true)) {
            val seed = Color(0xFF607D8B)
            val fixed = abColorScheme(seed.toArgb(), dark, DisplayColorMode.NORMAL)
            val plain = dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false,
                style = PaletteStyle.TonalSpot)
            assertTrue(spread(fixed.primary) * 2 < spread(plain.primary), "near-grey must stay muted, dark=$dark")
        }
        val brown = 0xFF8B5A2B.toInt()
        val fixed = abColorScheme(brown, false, DisplayColorMode.NORMAL)
        assertNotEquals(0xFF87521B.toInt(), fixed.primary.toArgb(), "do not force plain TonalSpot's orange primary")
        val drift = hueDistance(fixed.primary.toArgb(), brown)
        assertTrue(drift < 10f, "a capped low-chroma seed keeps its hue (drift $drift)")
    }

    @Test
    fun einkDefaultsKeepTheirExistingGreyRoles() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                val bw = abColorScheme(seed, dark, DisplayColorMode.BW)
                assertEquals(if (dark) 0xFFC9C9C9.toInt() else 0xFF606060.toInt(), bw.primary.toArgb())
                assertEquals(if (dark) 0xFF131313.toInt() else 0xFFFAFAFA.toInt(), bw.surface.toArgb())
                // #4069: COLOR_EINK shares the MONOCHROME ink/paper scheme.
                for (mode in listOf(DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK)) {
                    val mono = abColorScheme(seed, dark, mode)
                    assertEquals(if (dark) Color.White else Color.Black, mono.primary)
                    assertEquals(if (dark) Color.Black else Color.White, mono.surface)
                    assertEquals(Color.Transparent, mono.scrim)
                }
            }
        }
    }
}

private fun hueDistance(a: Int, b: Int): Float {
    val d = kotlin.math.abs(hueOf(a) - hueOf(b))
    return if (d > 180f) 360f - d else d
}

private fun hueOf(argb: Int): Float {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val c = max - min
    if (c == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / c) % 6f
        g -> ((b - r) / c) + 2f
        else -> ((r - g) / c) + 4f
    } * 60f
    return if (h < 0f) h + 360f else h
}
