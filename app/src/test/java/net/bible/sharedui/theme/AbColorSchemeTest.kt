package net.bible.sharedui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORANGE = 0xFFFF8000.toInt()

/**
 * `ColorScheme` does not override `equals`, so two structurally identical schemes are not equal.
 * Its `toString()` enumerates every role, which makes it a complete structural comparison — that
 * completeness is the point: the identity test below must cover roles nobody thought to list.
 */
private fun schemeOf(seed: Int?, dark: Boolean, mode: DisplayColorMode) =
    abColorScheme(seed, dark, mode).toString()

class AbColorSchemeTest {
    @Test
    fun `no seed returns today's stock scheme, role for role`() {
        assertEquals(lightColorScheme().toString(), schemeOf(null, false, DisplayColorMode.NORMAL))
        assertEquals(darkColorScheme().toString(), schemeOf(null, true, DisplayColorMode.NORMAL))
    }

    @Test
    fun `the default workspace colour is the not-set sentinel`() {
        assertEquals(
            schemeOf(null, false, DisplayColorMode.NORMAL),
            schemeOf(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL),
        )
    }

    @Test
    fun `a seed moves primary towards the seed's hue`() {
        val seeded = abColorScheme(ORANGE, false, DisplayColorMode.NORMAL)
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
