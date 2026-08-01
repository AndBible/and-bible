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

    // ---- Batch 4c: classic colour-picker maths ----

    @Test fun shade_blends_towards_white_for_positive_and_black_for_negative() {
        // Classic: t = (percent < 0) ? 0 : 255; p = |percent|; ch' = round((t - ch) * p) + ch.
        // 0x336699 at +0.5 -> r: round((255-51)*0.5)+51 = 153, g: round((255-102)*0.5)+102 = 179 (178.5
        // rounds HALF UP, Java Math.round semantics), b: round((255-153)*0.5)+153 = 204.
        assertEquals(0xFF99B3CC.toInt(), shadeArgb(0xFF336699.toInt(), 0.5))
        // -0.5 -> ch' = round((0-ch)*0.5)+ch = ceil(ch/2) for the half cases: r 26, g 51, b 77
        // (floor(-25.5 + 0.5) = -25, so r = 26; with kotlin.math.round g would come out 50, not 51).
        assertEquals(0xFF1A334D.toInt(), shadeArgb(0xFF336699.toInt(), -0.5))
        assertEquals(0xFF.toInt(), (shadeArgb(0xFF336699.toInt(), 0.9) ushr 24) and 0xFF)
    }

    @Test fun shade_saturates_at_the_extremes() {
        assertEquals(0xFFFFFFFF.toInt(), shadeArgb(0xFFFFFFFF.toInt(), 0.9), "white cannot get lighter")
        assertEquals(0xFF000000.toInt(), shadeArgb(0xFF000000.toInt(), -0.775), "black cannot get darker")
    }

    @Test fun color_shades_are_twelve_light_to_dark_and_exclude_the_input() {
        val base = 0xFF2196F3.toInt()
        val shades = colorShades(base)
        assertEquals(12, shades.size)
        assertTrue(shades.none { it == base }, "classic's percentages never reproduce the input")
        val lum = shades.map { relativeLuminance(it) }
        assertEquals(lum.sortedDescending(), lum, "ordered lightest -> darkest")
    }

    @Test fun presets_append_black_when_the_current_colour_is_already_material() {
        val list = presetColors(0xFF2196F3.toInt()) // BLUE 500 is in MATERIAL_PRESETS
        assertEquals(20, list.size)
        assertEquals(MATERIAL_PRESETS, list.dropLast(1), "the 19 keep classic's order")
        assertEquals(BLACK_ARGB, list.last(), "black fills 19 -> 20")
    }

    @Test fun presets_unshift_a_non_material_colour_and_then_skip_black() {
        val custom = 0xFF123456.toInt()
        val list = presetColors(custom)
        assertEquals(20, list.size)
        assertEquals(custom, list.first())
        assertTrue(BLACK_ARGB !in list, "already 20 long, so classic does not push black")
        assertEquals(1, list.count { it == custom }, "never duplicated")
    }

    @Test fun presets_carry_both_the_working_and_the_original_colour() {
        val working = 0xFF123456.toInt()
        val original = 0xFF654321.toInt()
        val list = presetColors(working, original)
        assertEquals(21, list.size)
        assertEquals(original, list[0], "the original is unshifted last, so it ends up first")
        assertEquals(working, list[1])
    }

    @Test fun light_colour_test_matches_classics_065_luminance_threshold() {
        // Classic tints the check mark black on light swatches (ColorUtils.calculateLuminance >= 0.65).
        assertTrue(isLightColor(0xFFFFEB3B.toInt()), "Material yellow is light")
        assertTrue(isLightColor(0xFFFFFFFF.toInt()))
        assertTrue(!isLightColor(0xFF3F51B5.toInt()), "Material indigo is dark")
        assertTrue(!isLightColor(0xFF000000.toInt()))
    }
}
