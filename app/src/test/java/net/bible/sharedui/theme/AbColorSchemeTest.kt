package net.bible.sharedui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORANGE = 0xFFFF8000.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val LIGHT_GREY = 0xFFAAAAAA.toInt()

/**
 * `ColorScheme` does not override `equals`, so two structurally identical schemes are not equal.
 * Its `toString()` enumerates every role, which makes it a complete structural comparison — that
 * completeness is the point: structural comparisons must cover roles nobody thought to list.
 */
private fun schemeOf(
    seed: Int?, dark: Boolean, mode: DisplayColorMode, style: ThemePaletteStyle = ThemePaletteStyle.DEFAULT,
) = abColorScheme(seed, dark, mode, style).toString()

/** max-min over the RGB channels: 0 for a pure grey; a few units for Material's warm-tinted neutrals. */
private fun rgbSpread(argb: Int): Int {
    val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
    return maxOf(r, g, b) - minOf(r, g, b)
}

class AbColorSchemeTest {
    @Test
    fun `NORMAL never uses the stock lilac scheme, even with no seed`() {
        assertNotEquals(lightColorScheme().toString(), schemeOf(null, false, DisplayColorMode.NORMAL))
        assertNotEquals(darkColorScheme().toString(), schemeOf(null, true, DisplayColorMode.NORMAL))
    }

    @Test
    fun `in NORMAL a null seed is the default workspace colour`() {
        ThemePaletteStyle.entries.forEach { style ->
            listOf(false, true).forEach { dark ->
                assertEquals(
                    "$style dark=$dark",
                    schemeOf(DEFAULT_WORKSPACE_COLOR_ARGB, dark, DisplayColorMode.NORMAL, style),
                    schemeOf(null, dark, DisplayColorMode.NORMAL, style),
                )
            }
        }
    }

    /** The reported bug: black came out pink-red and greys teal under TonalSpot. */
    @Test
    fun `black and grey seeds stay achromatic under the default style`() {
        listOf(BLACK, DEFAULT_WORKSPACE_COLOR_ARGB, LIGHT_GREY).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val s = abColorScheme(seed, dark, DisplayColorMode.NORMAL)
                listOf("primary" to s.primary, "primaryContainer" to s.primaryContainer,
                    "secondaryContainer" to s.secondaryContainer, "surface" to s.surface).forEach { (n, c) ->
                    assertTrue(
                        "seed #%06X dark=$dark: $n #%06X has RGB spread %d".format(
                            0xFFFFFF and seed, 0xFFFFFF and c.toArgb(), rgbSpread(c.toArgb())),
                        rgbSpread(c.toArgb()) <= 8,
                    )
                }
            }
        }
    }

    /** Fidelity's defining property, and what makes the default toolbar classic grey. */
    @Test
    fun `Fidelity keeps the seed itself as primaryContainer`() {
        val s = abColorScheme(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL, ThemePaletteStyle.FIDELITY)
        assertEquals(DEFAULT_WORKSPACE_COLOR_ARGB, s.primaryContainer.toArgb())
    }

    /** Catches a mapping slip (two enum entries wired to the same PaletteStyle). */
    @Test
    fun `every style yields a distinct scheme`() {
        val schemes = ThemePaletteStyle.entries.associateWith { schemeOf(ORANGE, false, DisplayColorMode.NORMAL, it) }
        assertEquals(ThemePaletteStyle.entries.size, schemes.values.toSet().size)
    }

    @Test
    fun `BW ignores the style`() {
        val first = schemeOf(ORANGE, false, DisplayColorMode.BW, ThemePaletteStyle.entries.first())
        ThemePaletteStyle.entries.forEach { assertEquals("$it", first, schemeOf(ORANGE, false, DisplayColorMode.BW, it)) }
    }

    /** COLOR_EINK stays on TonalSpot: equal contrast for every workspace colour on e-ink. */
    @Test
    fun `COLOR_EINK ignores the style and keeps today's TonalSpot-then-grey result`() {
        val tonal = schemeOf(ORANGE, false, DisplayColorMode.COLOR_EINK, ThemePaletteStyle.TONAL_SPOT)
        ThemePaletteStyle.entries.forEach { assertEquals("$it", tonal, schemeOf(ORANGE, false, DisplayColorMode.COLOR_EINK, it)) }
        // unset colour in COLOR_EINK is still the stock scheme, greyscaled
        assertEquals(
            schemeOf(null, false, DisplayColorMode.COLOR_EINK),
            schemeOf(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.COLOR_EINK),
        )
    }

    /** Independent pre-change contract: set seeds use TonalSpot; unset seeds use stock roles. */
    @Test
    fun `COLOR_EINK preserves TonalSpot set-seed roles in light and dark`() {
        for (dark in listOf(false, true)) {
            val tonal = abColorScheme(ORANGE, dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT)
            for (style in ThemePaletteStyle.entries) {
                assertLegacyGreyRoles("set dark=$dark style=$style", tonal,
                    abColorScheme(ORANGE, dark, DisplayColorMode.COLOR_EINK, style))
            }
        }
    }

    @Test
    fun `COLOR_EINK preserves stock unset-seed roles in light and dark`() {
        for (dark in listOf(false, true)) {
            val stock = if (dark) darkColorScheme() else lightColorScheme()
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                for (style in ThemePaletteStyle.entries) {
                    assertLegacyGreyRoles("unset seed=$seed dark=$dark style=$style", stock,
                        abColorScheme(seed, dark, DisplayColorMode.COLOR_EINK, style))
                }
            }
        }
    }

    @Test
    fun `a seed moves primary towards the seed's hue`() {
        val seeded = abColorScheme(ORANGE, false, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT)
        val stock = lightColorScheme()
        assertTrue(
            "seeded primary should be nearer the seed hue than the stock primary is",
            hueDistance(seeded.primary.toArgb(), ORANGE) < hueDistance(stock.primary.toArgb(), ORANGE),
        )
    }

    @Test
    fun `BW ignores the seed`() {
        assertEquals(
            schemeOf(null, false, DisplayColorMode.BW),
            schemeOf(ORANGE, false, DisplayColorMode.BW),
        )
    }

    @Test
    fun `COLOR_EINK seeds, and differs from BW`() {
        assertNotEquals(
            schemeOf(null, false, DisplayColorMode.COLOR_EINK),
            schemeOf(ORANGE, false, DisplayColorMode.COLOR_EINK),
        )
    }

    /**
     * Spec §5: COLOR_EINK is seeded *and then greyscaled*. The greyscale pass used to live at
     * `AbTheme`'s call site, which meant the second consumer — the BibleView payload — emitted
     * seeded-but-coloured roles. It now lives inside `abColorScheme`, so a seeded COLOR_EINK scheme
     * is grey no matter who asks for it. `assertNotEquals` above keeps the *seeding* honest; this
     * keeps the *greyscaling* honest.
     */
    @Test
    fun `COLOR_EINK returns a fully greyscaled scheme, seed or no seed`() {
        assertAllGrey("seeded COLOR_EINK", abColorScheme(ORANGE, false, DisplayColorMode.COLOR_EINK))
        assertAllGrey("seeded COLOR_EINK (dark)", abColorScheme(ORANGE, true, DisplayColorMode.COLOR_EINK))
        assertAllGrey("unseeded COLOR_EINK", abColorScheme(null, false, DisplayColorMode.COLOR_EINK))
    }

    @Test
    fun `BW returns a fully greyscaled scheme`() {
        assertAllGrey("BW", abColorScheme(ORANGE, false, DisplayColorMode.BW))
        assertAllGrey("BW (dark)", abColorScheme(ORANGE, true, DisplayColorMode.BW))
    }

    /** The counterpart of the two above: NORMAL must NOT be greyscaled, or the pass is a no-op bug. */
    @Test
    fun `NORMAL keeps its hues`() {
        val roles = rolesOf(abColorScheme(ORANGE, false, DisplayColorMode.NORMAL))
        assertTrue(
            "a seeded NORMAL scheme must contain coloured roles",
            roles.any { (_, argb) -> !isGrey(argb) },
        )
    }
}

/**
 * The roles the Vue payload ships plus a spread of the rest (error/surface/outline), named so a
 * failure says which one leaked colour. Not the full 36 — the point is to catch "the pass did not
 * run", which shows up on any of these.
 */
private fun rolesOf(s: androidx.compose.material3.ColorScheme): List<Pair<String, Int>> = listOf(
    "primary" to s.primary.toArgb(),
    "onPrimary" to s.onPrimary.toArgb(),
    "primaryContainer" to s.primaryContainer.toArgb(),
    "onPrimaryContainer" to s.onPrimaryContainer.toArgb(),
    "secondaryContainer" to s.secondaryContainer.toArgb(),
    "onSecondaryContainer" to s.onSecondaryContainer.toArgb(),
    "tertiary" to s.tertiary.toArgb(),
    "error" to s.error.toArgb(),
    "surface" to s.surface.toArgb(),
    "background" to s.background.toArgb(),
    "outline" to s.outline.toArgb(),
    "surfaceTint" to s.surfaceTint.toArgb(),
)

/** Legacy integer BT.601 transform, independent of production's grayscale helper. */
private fun assertLegacyGreyRoles(
    what: String,
    base: androidx.compose.material3.ColorScheme,
    actual: androidx.compose.material3.ColorScheme,
) {
    val actualRoles = rolesOf(actual).toMap()
    for ((name, argb) in rolesOf(base)) {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val y = (299 * r + 587 * g + 114 * b) / 1000
        val expected = (argb and 0xFF000000.toInt()) or (y shl 16) or (y shl 8) or y
        assertEquals("$what role=$name", expected, actualRoles.getValue(name))
    }
}

private fun isGrey(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r == g && g == b
}

private fun assertAllGrey(what: String, scheme: androidx.compose.material3.ColorScheme) {
    rolesOf(scheme).forEach { (name, argb) ->
        assertTrue("$what: $name should be grey but was #%06X".format(0xFFFFFF and argb), isGrey(argb))
    }
}

/** Circular distance in degrees between the hues of two ARGB colours. */
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
