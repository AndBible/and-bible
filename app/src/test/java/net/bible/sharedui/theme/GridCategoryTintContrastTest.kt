package net.bible.sharedui.theme

import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.theme.categoryBaseArgb
import net.bible.sharedcore.theme.categoryChipArgb
import net.bible.sharedcore.theme.contrastRatio
import net.bible.sharedcore.theme.hexOf
import net.bible.sharedcore.theme.hsvToArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The readability half of the "Choose passage" category-tint guarantee.
 *
 * `ColorMathTest` proves the scheme-independent part: the chip lands at the surface's own luminance.
 * That is what makes readability *plausible* for any seed, but it is not a contrast figure, and A/B
 * batch 4b made the M3 scheme seed-derived — `surfaceVariant`/`onSurfaceVariant` depend on the
 * workspace colour, so no fixed fixture covers the space. This test measures the real thing instead:
 * the stock schemes exactly, and 96 seeded schemes over the hue/saturation wheel.
 *
 * This is also the test that killed the design's first mechanism. A fixed blend towards the *raw*
 * palette was specified at 25%; measured here it needed to come down to **0.06** to clear AA
 * everywhere, which signals nothing. Matching the category colour's luminance to the surface first
 * removed the trade-off entirely.
 *
 * WCAG AA for normal-size text is 4.5:1. The grid's labels are small (`labelMedium`), so 4.5 is the
 * floor being defended, not a nice-to-have.
 */
private const val AA_NORMAL = 4.5

/** The categories plus the OTHER fallback that deuterocanonical books land on. */
private val GROUPS = (0..9).toList() + listOf(-1)

/**
 * The production chip colour, evaluated outside a composition. `categoryChipColor` is `@Composable`
 * only because it reads `LocalDisplayColorMode`; passing [mode] explicitly reaches the same
 * `categoryChipArgb`, so this test exercises the shipped formula rather than a copy of it.
 */
private fun chipArgb(surfaceVariantArgb: Int, group: Int, mode: DisplayColorMode): Int =
    categoryChipArgb(surfaceVariantArgb, accentArgbFor(categoryBaseArgb(group), mode))

private fun assertAllCategoriesReadable(what: String, seed: Int?, dark: Boolean, mode: DisplayColorMode) {
    val scheme = abColorScheme(seed, dark, mode)
    val surfaceVariant = scheme.surfaceVariant.toArgb()
    val onSurfaceVariant = scheme.onSurfaceVariant.toArgb()
    for (group in GROUPS) {
        val chip = chipArgb(surfaceVariant, group, mode)
        val ratio = contrastRatio(onSurfaceVariant, chip)
        assertTrue(
            "$what, category $group: text #${hexOf(onSurfaceVariant)} on chip #${hexOf(chip)} " +
                "(from surfaceVariant #${hexOf(surfaceVariant)}) is only " +
                "${(ratio * 100).toInt() / 100.0}:1, under $AA_NORMAL:1",
            ratio >= AA_NORMAL,
        )
    }
}

class GridCategoryTintContrastTest {
    @Test
    fun `the stock light and dark schemes keep every category readable`() {
        assertAllCategoriesReadable("stock light", null, dark = false, DisplayColorMode.NORMAL)
        assertAllCategoriesReadable("stock dark", null, dark = true, DisplayColorMode.NORMAL)
    }

    /**
     * The case a fixed fixture cannot cover: a user-chosen workspace colour reshapes the whole
     * scheme, `surfaceVariant` included. Sweeping the wheel at two saturations and two values is not
     * exhaustive over 2^24 seeds, but it is 96 real MaterialKolor schemes rather than an assumption,
     * and `surfaceVariant` is a low-chroma neutral role so it moves little across seeds.
     */
    @Test
    fun `a seeded scheme keeps every category readable, across the hue wheel`() {
        for (hue in 0 until 360 step 15) {
            for (sat in listOf(0.4f, 1.0f)) {
                for (value in listOf(0.5f, 1.0f)) {
                    val seed = hsvToArgb(hue.toFloat(), sat, value)
                    for (dark in listOf(false, true)) {
                        assertAllCategoriesReadable(
                            "seed #${hexOf(seed)} ${if (dark) "dark" else "light"}",
                            seed, dark, DisplayColorMode.NORMAL,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `COLOR_EINK keeps every category readable`() {
        assertAllCategoriesReadable("COLOR_EINK light", 0xFFFF8000.toInt(), false, DisplayColorMode.COLOR_EINK)
        assertAllCategoriesReadable("COLOR_EINK dark", 0xFFFF8000.toInt(), true, DisplayColorMode.COLOR_EINK)
    }

    /**
     * BW is where the tint is supposed to all but disappear: `accentArgbFor` greys the category
     * colour, so the chip becomes a slightly different grey rather than a hue. Asserted as a bound on
     * how far the chip may drift from the plain surface, so a future change that made the tint
     * *visible* in monochrome — against AndBible's monochrome doctrine — fails here.
     *
     * The bound is a measured value, not a target: greyed category colours do not all collapse to the
     * same grey, so a faint level difference between book groups does remain on an e-ink screen.
     */
    @Test
    fun `BW leaves the chip within a hair of the plain surface`() {
        for (dark in listOf(false, true)) {
            val scheme = abColorScheme(null, dark, DisplayColorMode.BW)
            val surfaceVariant = scheme.surfaceVariant.toArgb()
            for (group in GROUPS) {
                val chip = chipArgb(surfaceVariant, group, DisplayColorMode.BW)
                val drift = contrastRatio(surfaceVariant, chip)
                assertTrue(
                    "BW ${if (dark) "dark" else "light"}, category $group: chip #${hexOf(chip)} vs " +
                        "surface #${hexOf(surfaceVariant)} drifted ${(drift * 100).toInt() / 100.0}:1",
                    drift <= 1.6,
                )
                assertTrue(
                    "BW must not leave a hue in the chip: #${hexOf(chip)}",
                    ((chip shr 16) and 0xFF) == ((chip shr 8) and 0xFF) &&
                        ((chip shr 8) and 0xFF) == (chip and 0xFF),
                )
            }
        }
    }
}
