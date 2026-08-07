package net.bible.android.view.activity.settings

import net.bible.android.database.WorkspaceEntities.Colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorThemePresetTest {
    private fun blank() = Colors(null, null, null, null, null, null, null, null, null, null)

    @Test
    fun applyToStampsAllFieldsAndThemeName() {
        val c = blank()
        ColorThemePreset.GRUVBOX.applyTo(c)
        assertEquals("gruvbox", c.themeName)
        assertEquals(ColorThemePreset.GRUVBOX.dayText, c.dayTextColor)
        assertEquals(ColorThemePreset.GRUVBOX.dayBg, c.dayBackground)
        assertEquals(ColorThemePreset.GRUVBOX.dayLink, c.dayLinkColor)
        assertEquals(ColorThemePreset.GRUVBOX.dayVerse, c.dayVerseNumberColor)
        assertEquals(ColorThemePreset.GRUVBOX.dayHeading, c.dayHeadingColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightText, c.nightTextColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightBg, c.nightBackground)
        assertEquals(ColorThemePreset.GRUVBOX.nightLink, c.nightLinkColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightVerse, c.nightVerseNumberColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightHeading, c.nightHeadingColor)
    }

    @Test
    fun byIdRoundTrips() {
        for (p in ColorThemePreset.entries) assertEquals(p, ColorThemePreset.byId(p.id))
    }

    @Test
    fun byIdUnknownOrNullIsNull() {
        assertNull(ColorThemePreset.byId(null))
        assertNull(ColorThemePreset.byId(""))
        assertNull(ColorThemePreset.byId("nonexistent"))
    }

    @Test
    fun applyToLeavesNoiseAndImagesUntouched() {
        val c = blank().copy(dayNoise = 30, dayBackgroundImage = "BGIMG_x")
        ColorThemePreset.NORD.applyTo(c)
        assertEquals(30, c.dayNoise)
        assertEquals("BGIMG_x", c.dayBackgroundImage)
    }
}
