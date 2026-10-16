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
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLUE = 0xFF1565C0.toInt()   // HCT chroma ~56 >= every TonalSpot chroma: no cap applies
private const val BROWN = 0xFF8B5A2B.toInt()  // HCT chroma ~31.7 < 36: primary cap applies
private const val SLATE = 0xFF607D8B.toInt()  // HCT chroma ~11: near-grey with a hint of hue

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
    fun `MONOCHROME puts every role on ink or paper light and dark seed or not`() {
        for (dark in listOf(false, true)) for (seed in listOf(null, ORANGE)) {
            val scheme = abColorScheme(seed, dark, DisplayColorMode.MONOCHROME)
            allRolesOf(scheme).filter { it.first != "scrim" }.forEach { (name, argb) ->
                assertTrue("dark=$dark seed=$seed: $name was #%08X".format(argb),
                    argb == Color.Black.toArgb() || argb == Color.White.toArgb())
            }
            assertEquals("scrim must be transparent", 0, scheme.scrim.toArgb() ushr 24)
        }
    }

    @Test
    fun `MONOCHROME inverts selection`() {
        for (dark in listOf(false, true)) {
            val s = abColorScheme(null, dark, DisplayColorMode.MONOCHROME)
            val ink = if (dark) Color.White else Color.Black
            val paper = if (dark) Color.Black else Color.White
            assertEquals(ink, s.primaryContainer)
            assertEquals(paper, s.onPrimaryContainer)
            assertEquals(paper, s.surface)
            assertEquals(ink, s.onSurface)
        }
    }

    @Test
    fun `BW and COLOR_EINK retain all pre Task 2 role values`() {
        legacyRoles.forEach { (key, expected) ->
            val (mode, dark, seed) = key.split("|")
            val actual = allRolesOf(abColorScheme(seed.toIntOrNull(), dark.toBoolean(), DisplayColorMode.valueOf(mode)))
            assertEquals(key, expected.split(","), actual.map { "%08X".format(it.second) })
        }
    }

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

    private fun visibleRoles(s: androidx.compose.material3.ColorScheme) = listOf(
        "primary" to s.primary, "primaryContainer" to s.primaryContainer,
        "secondaryContainer" to s.secondaryContainer, "tertiaryContainer" to s.tertiaryContainer,
        "surface" to s.surface,
    )

    /** The reported bug: black came out pink-red and greys teal under TonalSpot. */
    @Test
    fun `black grey and white seeds stay achromatic under the grey-safe default`() {
        assertEquals(ThemePaletteStyle.TONAL_SPOT_GREY, ThemePaletteStyle.DEFAULT)
        listOf(BLACK, DEFAULT_WORKSPACE_COLOR_ARGB, LIGHT_GREY, WHITE).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val s = abColorScheme(seed, dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT_GREY)
                visibleRoles(s).forEach { (n, c) ->
                    assertTrue(
                        "seed #%06X dark=$dark: $n #%06X has RGB spread %d".format(
                            0xFFFFFF and seed, 0xFFFFFF and c.toArgb(), rgbSpread(c.toArgb())),
                        rgbSpread(c.toArgb()) <= 4,
                    )
                }
            }
        }
    }

    /** Pins that the original TonalSpot is kept as is (the grey bug included), not silently fixed. */
    @Test
    fun `original TonalSpot still colours a grey seed`() {
        val s = abColorScheme(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT)
        assertTrue(rgbSpread(s.primaryContainer.toArgb()) > 40)
    }

    /** With seed chroma >= 36 no cap applies, so a colourful workspace looks exactly as before. */
    @Test
    fun `grey-safe equals TonalSpot role for role for a saturated seed`() {
        listOf(false, true).forEach { dark ->
            assertEquals(
                "dark=$dark",
                schemeOf(BLUE, dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT),
                schemeOf(BLUE, dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT_GREY),
            )
        }
    }

    /** Brown keeps its hue and loses TonalSpot's forced chroma 36 (#87521B, more orange). */
    @Test
    fun `grey-safe caps a low-chroma seed but keeps its hue`() {
        val grey = abColorScheme(BROWN, false, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT_GREY)
        val tonal = abColorScheme(BROWN, false, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT)
        assertEquals(0xFF87521B.toInt(), tonal.primary.toArgb())
        assertNotEquals(tonal.primary.toArgb(), grey.primary.toArgb())
        assertTrue("hue drift %.1f".format(hueDistance(grey.primary.toArgb(), BROWN)),
            hueDistance(grey.primary.toArgb(), BROWN) < 10f)
    }

    /** A near-grey stays muted: its primary is much less saturated than TonalSpot's. */
    @Test
    fun `grey-safe keeps a near-grey muted`() {
        listOf(false, true).forEach { dark ->
            val grey = abColorScheme(SLATE, dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT_GREY)
            val tonal = abColorScheme(SLATE, dark, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT)
            assertTrue(
                "dark=$dark grey-safe spread ${rgbSpread(grey.primary.toArgb())} vs TonalSpot ${rgbSpread(tonal.primary.toArgb())}",
                rgbSpread(grey.primary.toArgb()) * 2 < rgbSpread(tonal.primary.toArgb()),
            )
        }
    }

    /** Fidelity's defining property: the seed itself is primaryContainer. */
    @Test
    fun `Fidelity keeps the seed itself as primaryContainer`() {
        val s = abColorScheme(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL, ThemePaletteStyle.FIDELITY)
        assertEquals(DEFAULT_WORKSPACE_COLOR_ARGB, s.primaryContainer.toArgb())
    }

    /** Catches a mapping slip (two enum entries wired to the same PaletteStyle). */
    @Test
    fun `every style yields a distinct scheme`() {
        // ORANGE is saturated, where grey-safe equals TonalSpot by design; check grey-safe on a grey seed.
        val materialStyles = ThemePaletteStyle.entries - ThemePaletteStyle.TONAL_SPOT_GREY
        val schemes = materialStyles.associateWith { schemeOf(ORANGE, false, DisplayColorMode.NORMAL, it) }
        assertEquals(materialStyles.size, schemes.values.toSet().size)
        val greySafe = schemeOf(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL, ThemePaletteStyle.TONAL_SPOT_GREY)
        materialStyles.forEach {
            assertNotEquals("$it", schemeOf(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL, it), greySafe)
        }
    }

    @Test
    fun `BW ignores the style`() {
        val first = schemeOf(ORANGE, false, DisplayColorMode.BW, ThemePaletteStyle.entries.first())
        ThemePaletteStyle.entries.forEach { assertEquals("$it", first, schemeOf(ORANGE, false, DisplayColorMode.BW, it)) }
    }

    @Test
    fun `MONOCHROME ignores the style`() {
        val first = schemeOf(ORANGE, false, DisplayColorMode.MONOCHROME, ThemePaletteStyle.entries.first())
        ThemePaletteStyle.entries.forEach { assertEquals("$it", first, schemeOf(ORANGE, false, DisplayColorMode.MONOCHROME, it)) }
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

private fun allRolesOf(s: androidx.compose.material3.ColorScheme): List<Pair<String, Int>> = listOf(
    "primary" to s.primary.toArgb(),
    "onPrimary" to s.onPrimary.toArgb(),
    "primaryContainer" to s.primaryContainer.toArgb(),
    "onPrimaryContainer" to s.onPrimaryContainer.toArgb(),
    "inversePrimary" to s.inversePrimary.toArgb(),
    "secondary" to s.secondary.toArgb(),
    "onSecondary" to s.onSecondary.toArgb(),
    "secondaryContainer" to s.secondaryContainer.toArgb(),
    "onSecondaryContainer" to s.onSecondaryContainer.toArgb(),
    "tertiary" to s.tertiary.toArgb(),
    "onTertiary" to s.onTertiary.toArgb(),
    "tertiaryContainer" to s.tertiaryContainer.toArgb(),
    "onTertiaryContainer" to s.onTertiaryContainer.toArgb(),
    "background" to s.background.toArgb(),
    "onBackground" to s.onBackground.toArgb(),
    "surface" to s.surface.toArgb(),
    "onSurface" to s.onSurface.toArgb(),
    "surfaceVariant" to s.surfaceVariant.toArgb(),
    "onSurfaceVariant" to s.onSurfaceVariant.toArgb(),
    "surfaceTint" to s.surfaceTint.toArgb(),
    "inverseSurface" to s.inverseSurface.toArgb(),
    "inverseOnSurface" to s.inverseOnSurface.toArgb(),
    "error" to s.error.toArgb(),
    "onError" to s.onError.toArgb(),
    "errorContainer" to s.errorContainer.toArgb(),
    "onErrorContainer" to s.onErrorContainer.toArgb(),
    "outline" to s.outline.toArgb(),
    "outlineVariant" to s.outlineVariant.toArgb(),
    "scrim" to s.scrim.toArgb(),
    "surfaceBright" to s.surfaceBright.toArgb(),
    "surfaceDim" to s.surfaceDim.toArgb(),
    "surfaceContainer" to s.surfaceContainer.toArgb(),
    "surfaceContainerHigh" to s.surfaceContainerHigh.toArgb(),
    "surfaceContainerHighest" to s.surfaceContainerHighest.toArgb(),
    "surfaceContainerLow" to s.surfaceContainerLow.toArgb(),
    "surfaceContainerLowest" to s.surfaceContainerLowest.toArgb(),
)

// Captured at ec7f2fe19 before production edits; never regenerate to accept drift. NORMAL rows were
// dropped when Theme style made NORMAL style-derived with a default seed (covered by its own tests).
private val legacyRoles = mapOf(
    "BW|false|null" to "FF606060,FFFFFFFF,FFE4E4E4,FF141414,FFC9C9C9,FF5F5F5F,FFFFFFFF,FFE3E3E3,FF1C1C1C,FF606060,FFFFFFFF,FFE5E5E5,FF1B1B1B,FFFAFAFA,FF1C1C1C,FFFAFAFA,FF1C1C1C,FFE3E3E3,FF474747,FF606060,FF303030,FFF1F1F1,FF4F4F4F,FFFFFFFF,FFE5E5E5,FF1C1C1C,FF767676,FFC7C7C7,FF000000,FFFAFAFA,FFDADADA,FFEFEFEF,FFE8E8E8,FFE2E2E2,FFF4F4F4,FFFFFFFF",
    "BW|false|-32768" to "FF606060,FFFFFFFF,FFE4E4E4,FF141414,FFC9C9C9,FF5F5F5F,FFFFFFFF,FFE3E3E3,FF1C1C1C,FF606060,FFFFFFFF,FFE5E5E5,FF1B1B1B,FFFAFAFA,FF1C1C1C,FFFAFAFA,FF1C1C1C,FFE3E3E3,FF474747,FF606060,FF303030,FFF1F1F1,FF4F4F4F,FFFFFFFF,FFE5E5E5,FF1C1C1C,FF767676,FFC7C7C7,FF000000,FFFAFAFA,FFDADADA,FFEFEFEF,FFE8E8E8,FFE2E2E2,FFF4F4F4,FFFFFFFF",
    "BW|true|null" to "FFC9C9C9,FF2F2F2F,FF474747,FFE4E4E4,FF606060,FFC7C7C7,FF313131,FF484848,FFE3E3E3,FFCACACA,FF313131,FF484848,FFE5E5E5,FF131313,FFE2E2E2,FF131313,FFE2E2E2,FF474747,FFC7C7C7,FFC9C9C9,FFE2E2E2,FF303030,FFC9C9C9,FF2A2A2A,FF3D3D3D,FFE5E5E5,FF919191,FF474747,FF000000,FF393939,FF131313,FF202020,FF2A2A2A,FF353535,FF1C1C1C,FF0E0E0E",
    "BW|true|-32768" to "FFC9C9C9,FF2F2F2F,FF474747,FFE4E4E4,FF606060,FFC7C7C7,FF313131,FF484848,FFE3E3E3,FFCACACA,FF313131,FF484848,FFE5E5E5,FF131313,FFE2E2E2,FF131313,FFE2E2E2,FF474747,FFC7C7C7,FFC9C9C9,FFE2E2E2,FF303030,FFC9C9C9,FF2A2A2A,FF3D3D3D,FFE5E5E5,FF919191,FF474747,FF000000,FF393939,FF131313,FF202020,FF2A2A2A,FF353535,FF1C1C1C,FF0E0E0E",
    "COLOR_EINK|false|null" to "FF606060,FFFFFFFF,FFE4E4E4,FF141414,FFC9C9C9,FF5F5F5F,FFFFFFFF,FFE3E3E3,FF1C1C1C,FF606060,FFFFFFFF,FFE5E5E5,FF1B1B1B,FFFAFAFA,FF1C1C1C,FFFAFAFA,FF1C1C1C,FFE3E3E3,FF474747,FF606060,FF303030,FFF1F1F1,FF4F4F4F,FFFFFFFF,FFE5E5E5,FF1C1C1C,FF767676,FFC7C7C7,FF000000,FFFAFAFA,FFDADADA,FFEFEFEF,FFE8E8E8,FFE2E2E2,FFF4F4F4,FFFFFFFF",
    "COLOR_EINK|false|-32768" to "FF5C5C5C,FFFFFFFF,FFE4E4E4,FF434343,FFC7C7C7,FF5E5E5E,FFFFFFFF,FFE4E4E4,FF464646,FF5B5B5B,FFFFFFFF,FFDFDFDF,FF434343,FFF9F9F9,FF1B1B1B,FFF9F9F9,FF1B1B1B,FFE3E3E3,FF474747,FF5C5C5C,FF303030,FFF1F1F1,FF494949,FFFFFFFF,FFE4E4E4,FF131313,FF777777,FFC7C7C7,FF000000,FFF9F9F9,FFDADADA,FFEFEFEF,FFE9E9E9,FFE3E3E3,FFF4F4F4,FFFFFFFF",
    "COLOR_EINK|true|null" to "FFC9C9C9,FF2F2F2F,FF474747,FFE4E4E4,FF606060,FFC7C7C7,FF313131,FF484848,FFE3E3E3,FFCACACA,FF313131,FF484848,FFE5E5E5,FF131313,FFE2E2E2,FF131313,FFE2E2E2,FF474747,FFC7C7C7,FFC9C9C9,FFE2E2E2,FF303030,FFC9C9C9,FF2A2A2A,FF3D3D3D,FFE5E5E5,FF919191,FF474747,FF000000,FF393939,FF131313,FF202020,FF2A2A2A,FF353535,FF1C1C1C,FF0E0E0E",
    "COLOR_EINK|true|-32768" to "FFC7C7C7,FF2D2D2D,FF434343,FFE4E4E4,FF5C5C5C,FFC7C7C7,FF303030,FF464646,FFE4E4E4,FFC3C3C3,FF2D2D2D,FF434343,FFDFDFDF,FF131313,FFE3E3E3,FF131313,FFE3E3E3,FF474747,FFC7C7C7,FFC7C7C7,FFE3E3E3,FF303030,FFC9C9C9,FF1F1F1F,FF2D2D2D,FFE4E4E4,FF919191,FF474747,FF000000,FF393939,FF131313,FF1F1F1F,FF2A2A2A,FF353535,FF1B1B1B,FF0E0E0E",
)
