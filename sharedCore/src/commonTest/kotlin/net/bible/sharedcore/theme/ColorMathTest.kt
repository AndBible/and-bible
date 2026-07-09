package net.bible.sharedcore.theme

import net.bible.service.common.DisplayColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorMathTest {
    @Test fun grayscale_collapses_rgb_to_equal_channels() {
        val g = toGrayscaleArgb(0xFFCC3300.toInt())
        val r = (g shr 16) and 0xFF; val gr = (g shr 8) and 0xFF; val b = g and 0xFF
        assertEquals(r, gr); assertEquals(gr, b)
        assertEquals(0xFF, (g ushr 24) and 0xFF, "alpha preserved")
        // A red "error"-role color must gray to equal channels too: this is the color math behind
        // AbTheme.grayscale() graying the ENTIRE M3 scheme (error included) in BW/COLOR_EINK.
        val err = toGrayscaleArgb(0xFFBA1A1A.toInt())
        val er = (err shr 16) and 0xFF; val eg = (err shr 8) and 0xFF; val eb = err and 0xFF
        assertTrue(er == eg && eg == eb, "error role must be grayscale in monochrome")
    }
    @Test fun accent_stays_colored_in_normal_and_color_eink_but_grays_in_bw() {
        val base = 0xFF2196F3.toInt()
        assertEquals(base, accentArgbFor(base, DisplayColorMode.NORMAL))
        assertEquals(base, accentArgbFor(base, DisplayColorMode.COLOR_EINK))
        val bw = accentArgbFor(base, DisplayColorMode.BW)
        val r = (bw shr 16) and 0xFF; val g = (bw shr 8) and 0xFF; val b = bw and 0xFF
        assertTrue(r == g && g == b, "BW accent must be gray")
    }
}
