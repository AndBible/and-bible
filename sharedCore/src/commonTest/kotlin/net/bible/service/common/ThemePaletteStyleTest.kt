package net.bible.service.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemePaletteStyleTest {
    @Test
    fun everyStyleRoundTripsThroughItsStoredValue() {
        ThemePaletteStyle.entries.forEach { assertEquals(it, ThemePaletteStyle.fromValue(it.value)) }
    }

    @Test
    fun unknownOrMissingValueIsNullSoCallersCanDefault() {
        assertNull(ThemePaletteStyle.fromValue(null))
        assertNull(ThemePaletteStyle.fromValue(""))
        assertNull(ThemePaletteStyle.fromValue("FIDELITY")) // stored values are lower-case ids
        assertNull(ThemePaletteStyle.fromValue("no_such_style"))
    }

    @Test
    fun defaultIsGreySafeTonalSpotAndListedFirst() {
        assertEquals(ThemePaletteStyle.TONAL_SPOT_GREY, ThemePaletteStyle.DEFAULT)
        assertEquals(ThemePaletteStyle.DEFAULT, ThemePaletteStyle.entries.first())
    }

    @Test
    fun storedValuesArePinned() {
        // These strings are persisted in user preferences and in arrays.xml; renaming one silently
        // resets every user who chose it.
        assertEquals(
            listOf("tonal_spot_grey", "fidelity", "tonal_spot", "content", "neutral", "monochrome",
                "vibrant", "expressive", "rainbow", "fruit_salad"),
            ThemePaletteStyle.entries.map { it.value },
        )
    }
}
