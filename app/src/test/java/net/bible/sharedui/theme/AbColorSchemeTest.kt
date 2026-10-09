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
    fun `old modes retain all pre Task 2 role values`() {
        legacyRoles.forEach { (key, expected) ->
            val (mode, dark, seed) = key.split("|")
            val actual = allRolesOf(abColorScheme(seed.toIntOrNull(), dark.toBoolean(), DisplayColorMode.valueOf(mode)))
            assertEquals(key, expected.split(","), actual.map { "%08X".format(it.second) })
        }
    }

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

// Captured at ec7f2fe19 before production edits; never regenerate to accept drift.
private val legacyRoles = mapOf(
    "NORMAL|false|null" to "FF6750A4,FFFFFFFF,FFEADDFF,FF21005D,FFD0BCFF,FF625B71,FFFFFFFF,FFE8DEF8,FF1D192B,FF7D5260,FFFFFFFF,FFFFD8E4,FF31111D,FFFEF7FF,FF1D1B20,FFFEF7FF,FF1D1B20,FFE7E0EC,FF49454F,FF6750A4,FF322F35,FFF5EFF7,FFB3261E,FFFFFFFF,FFF9DEDC,FF410E0B,FF79747E,FFCAC4D0,FF000000,FFFEF7FF,FFDED8E1,FFF3EDF7,FFECE6F0,FFE6E0E9,FFF7F2FA,FFFFFFFF",
    "NORMAL|false|-32768" to "FF8B4F24,FFFFFFFF,FFFFDCC7,FF6E390E,FFFFB787,FF755846,FFFFFFFF,FFFFDCC7,FF5B4130,FF606134,FFFFFFFF,FFE6E6AD,FF48491F,FFFFF8F5,FF221A15,FFFFF8F5,FF221A15,FFF4DED3,FF52443C,FF8B4F24,FF382E29,FFFFEDE5,FFBA1A1A,FFFFFFFF,FFFFDAD6,FF410002,FF84746A,FFD7C3B8,FF000000,FFFFF8F5,FFE7D7CE,FFFCEBE2,FFF6E5DC,FFF0DFD7,FFFFF1EA,FFFFFFFF",
    "NORMAL|true|null" to "FFD0BCFF,FF381E72,FF4F378B,FFEADDFF,FF6750A4,FFCCC2DC,FF332D41,FF4A4458,FFE8DEF8,FFEFB8C8,FF492532,FF633B48,FFFFD8E4,FF141218,FFE6E0E9,FF141218,FFE6E0E9,FF49454F,FFCAC4D0,FFD0BCFF,FFE6E0E9,FF322F35,FFF2B8B5,FF601410,FF8C1D18,FFF9DEDC,FF938F99,FF49454F,FF000000,FF3B383E,FF141218,FF211F26,FF2B2930,FF36343B,FF1D1B20,FF0F0D13",
    "NORMAL|true|-32768" to "FFFFB787,FF502400,FF6E390E,FFFFDCC7,FF8B4F24,FFE5BFA8,FF422B1B,FF5B4130,FFFFDCC7,FFCACA93,FF31320A,FF48491F,FFE6E6AD,FF19120D,FFF0DFD7,FF19120D,FFF0DFD7,FF52443C,FFD7C3B8,FFFFB787,FFF0DFD7,FF382E29,FFFFB4AB,FF690005,FF93000A,FFFFDAD6,FF9F8D83,FF52443C,FF000000,FF413731,FF19120D,FF261E19,FF312823,FF3D332D,FF221A15,FF140D08",
    "BW|false|null" to "FF606060,FFFFFFFF,FFE4E4E4,FF141414,FFC9C9C9,FF5F5F5F,FFFFFFFF,FFE3E3E3,FF1C1C1C,FF606060,FFFFFFFF,FFE5E5E5,FF1B1B1B,FFFAFAFA,FF1C1C1C,FFFAFAFA,FF1C1C1C,FFE3E3E3,FF474747,FF606060,FF303030,FFF1F1F1,FF4F4F4F,FFFFFFFF,FFE5E5E5,FF1C1C1C,FF767676,FFC7C7C7,FF000000,FFFAFAFA,FFDADADA,FFEFEFEF,FFE8E8E8,FFE2E2E2,FFF4F4F4,FFFFFFFF",
    "BW|false|-32768" to "FF606060,FFFFFFFF,FFE4E4E4,FF141414,FFC9C9C9,FF5F5F5F,FFFFFFFF,FFE3E3E3,FF1C1C1C,FF606060,FFFFFFFF,FFE5E5E5,FF1B1B1B,FFFAFAFA,FF1C1C1C,FFFAFAFA,FF1C1C1C,FFE3E3E3,FF474747,FF606060,FF303030,FFF1F1F1,FF4F4F4F,FFFFFFFF,FFE5E5E5,FF1C1C1C,FF767676,FFC7C7C7,FF000000,FFFAFAFA,FFDADADA,FFEFEFEF,FFE8E8E8,FFE2E2E2,FFF4F4F4,FFFFFFFF",
    "BW|true|null" to "FFC9C9C9,FF2F2F2F,FF474747,FFE4E4E4,FF606060,FFC7C7C7,FF313131,FF484848,FFE3E3E3,FFCACACA,FF313131,FF484848,FFE5E5E5,FF131313,FFE2E2E2,FF131313,FFE2E2E2,FF474747,FFC7C7C7,FFC9C9C9,FFE2E2E2,FF303030,FFC9C9C9,FF2A2A2A,FF3D3D3D,FFE5E5E5,FF919191,FF474747,FF000000,FF393939,FF131313,FF202020,FF2A2A2A,FF353535,FF1C1C1C,FF0E0E0E",
    "BW|true|-32768" to "FFC9C9C9,FF2F2F2F,FF474747,FFE4E4E4,FF606060,FFC7C7C7,FF313131,FF484848,FFE3E3E3,FFCACACA,FF313131,FF484848,FFE5E5E5,FF131313,FFE2E2E2,FF131313,FFE2E2E2,FF474747,FFC7C7C7,FFC9C9C9,FFE2E2E2,FF303030,FFC9C9C9,FF2A2A2A,FF3D3D3D,FFE5E5E5,FF919191,FF474747,FF000000,FF393939,FF131313,FF202020,FF2A2A2A,FF353535,FF1C1C1C,FF0E0E0E",
    "COLOR_EINK|false|null" to "FF606060,FFFFFFFF,FFE4E4E4,FF141414,FFC9C9C9,FF5F5F5F,FFFFFFFF,FFE3E3E3,FF1C1C1C,FF606060,FFFFFFFF,FFE5E5E5,FF1B1B1B,FFFAFAFA,FF1C1C1C,FFFAFAFA,FF1C1C1C,FFE3E3E3,FF474747,FF606060,FF303030,FFF1F1F1,FF4F4F4F,FFFFFFFF,FFE5E5E5,FF1C1C1C,FF767676,FFC7C7C7,FF000000,FFFAFAFA,FFDADADA,FFEFEFEF,FFE8E8E8,FFE2E2E2,FFF4F4F4,FFFFFFFF",
    "COLOR_EINK|false|-32768" to "FF5C5C5C,FFFFFFFF,FFE4E4E4,FF434343,FFC7C7C7,FF5E5E5E,FFFFFFFF,FFE4E4E4,FF464646,FF5B5B5B,FFFFFFFF,FFDFDFDF,FF434343,FFF9F9F9,FF1B1B1B,FFF9F9F9,FF1B1B1B,FFE3E3E3,FF474747,FF5C5C5C,FF303030,FFF1F1F1,FF494949,FFFFFFFF,FFE4E4E4,FF131313,FF777777,FFC7C7C7,FF000000,FFF9F9F9,FFDADADA,FFEFEFEF,FFE9E9E9,FFE3E3E3,FFF4F4F4,FFFFFFFF",
    "COLOR_EINK|true|null" to "FFC9C9C9,FF2F2F2F,FF474747,FFE4E4E4,FF606060,FFC7C7C7,FF313131,FF484848,FFE3E3E3,FFCACACA,FF313131,FF484848,FFE5E5E5,FF131313,FFE2E2E2,FF131313,FFE2E2E2,FF474747,FFC7C7C7,FFC9C9C9,FFE2E2E2,FF303030,FFC9C9C9,FF2A2A2A,FF3D3D3D,FFE5E5E5,FF919191,FF474747,FF000000,FF393939,FF131313,FF202020,FF2A2A2A,FF353535,FF1C1C1C,FF0E0E0E",
    "COLOR_EINK|true|-32768" to "FFC7C7C7,FF2D2D2D,FF434343,FFE4E4E4,FF5C5C5C,FFC7C7C7,FF303030,FF464646,FFE4E4E4,FFC3C3C3,FF2D2D2D,FF434343,FFDFDFDF,FF131313,FFE3E3E3,FF131313,FFE3E3E3,FF474747,FFC7C7C7,FFC7C7C7,FFE3E3E3,FF303030,FFC9C9C9,FF1F1F1F,FF2D2D2D,FFE4E4E4,FF919191,FF474747,FF000000,FF393939,FF131313,FF1F1F1F,FF2A2A2A,FF353535,FF1B1B1B,FF0E0E0E",
)
