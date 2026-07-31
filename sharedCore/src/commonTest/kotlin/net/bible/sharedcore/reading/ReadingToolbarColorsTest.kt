package net.bible.sharedcore.reading

import net.bible.service.common.DisplayColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingToolbarColorsTest {

    private val lightSurface = 0xFFFFFBFE.toInt()
    private val darkSurface = 0xFF1C1B1F.toInt()
    private val orange = 0xFFFF6D00.toInt()

    @Test
    fun nullIsNotSet() = assertFalse(isWorkspaceColorSet(null))

    @Test
    fun theClassicDefaultIsNotSet() = assertFalse(isWorkspaceColorSet(DEFAULT_WORKSPACE_COLOR_ARGB))

    @Test
    fun anyOtherColourIsSet() = assertTrue(isWorkspaceColorSet(orange))

    @Test
    fun notSetKeepsTheSurfaceInEveryMode() {
        for (mode in DisplayColorMode.entries) {
            for (night in listOf(false, true)) {
                val surface = if (night) darkSurface else lightSurface
                assertEquals(
                    surface,
                    readingToolbarContainerArgb(null, surface, night, mode),
                    "not-set must be a no-op (night=$night, mode=$mode)",
                )
                assertEquals(
                    surface,
                    readingToolbarContainerArgb(DEFAULT_WORKSPACE_COLOR_ARGB, surface, night, mode),
                    "the default sentinel must be a no-op (night=$night, mode=$mode)",
                )
            }
        }
    }

    @Test
    fun setInDayIsTheColourItself() {
        assertEquals(
            orange,
            readingToolbarContainerArgb(orange, lightSurface, nightMode = false, colorMode = DisplayColorMode.NORMAL),
        )
    }

    @Test
    fun setInDayIsGreyscaledInBw() {
        val result = readingToolbarContainerArgb(orange, lightSurface, false, DisplayColorMode.BW)
        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        assertTrue(r == g && g == b, "BW must be grey, got #${result.toUInt().toString(16)}")
    }

    @Test
    fun setInDayStaysColouredInColorEink() {
        assertEquals(orange, readingToolbarContainerArgb(orange, lightSurface, false, DisplayColorMode.COLOR_EINK))
    }

    @Test
    fun setInNightIsBlendedTowardsTheSurface() {
        val result = readingToolbarContainerArgb(orange, darkSurface, nightMode = true, colorMode = DisplayColorMode.NORMAL)
        // Exactly 30% of the way from the dark surface to the accent, per channel.
        assertEquals(lerpArgb(darkSurface, orange, NIGHT_WORKSPACE_TINT_FRACTION), result)
        // And it must stay much closer to the surface than to the raw colour.
        val red = (result shr 16) and 0xFF
        assertTrue(red < 0x80, "night tint must not be glaring, red=$red")
    }

    @Test
    fun lerpEndpointsAreExact() {
        assertEquals(darkSurface, lerpArgb(darkSurface, orange, 0f))
        assertEquals(orange, lerpArgb(darkSurface, orange, 1f))
    }

    @Test
    fun lerpKeepsFullAlpha() {
        val result = lerpArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0xFF, (result ushr 24) and 0xFF)
    }
}
