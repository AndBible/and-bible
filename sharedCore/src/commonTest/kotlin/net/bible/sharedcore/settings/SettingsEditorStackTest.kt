package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsEditorStackTest {

    @Test fun startsClosed() {
        val s = SettingsEditorStack()
        assertNull(s.current)
        assertEquals(0, s.depth)
        assertTrue(s.pages.value.isEmpty())
    }

    @Test fun openReplacesWhateverWasThere() {
        val s = SettingsEditorStack()
        s.open(SettingsEditorPage.Row("FONTSIZE"))
        s.push(SettingsEditorPage.Colors)
        s.open(SettingsEditorPage.Row("FONTFAMILY"))
        assertEquals(listOf(SettingsEditorPage.Row("FONTFAMILY")), s.pages.value)
        assertEquals(1, s.depth)
    }

    @Test fun pushStacksAndCurrentIsTheTop() {
        val s = SettingsEditorStack()
        s.open(SettingsEditorPage.Colors)
        s.push(SettingsEditorPage.ColorPick(ColorField.NIGHT_TEXT))
        assertEquals(2, s.depth)
        assertEquals(SettingsEditorPage.ColorPick(ColorField.NIGHT_TEXT), s.current)
    }

    @Test fun popRemovesOnePageAndClosesAtDepthOne() {
        val s = SettingsEditorStack()
        s.open(SettingsEditorPage.Colors)
        s.push(SettingsEditorPage.BackgroundImage(night = true))
        s.pop()
        assertEquals(SettingsEditorPage.Colors, s.current)
        s.pop()
        assertNull(s.current)
        assertEquals(0, s.depth)
    }

    @Test fun popOnAnEmptyStackIsANoOpNotAnUnderflow() {
        val s = SettingsEditorStack()
        s.pop()
        s.pop()
        assertEquals(0, s.depth)
    }

    @Test fun closeDropsEveryPage() {
        val s = SettingsEditorStack()
        s.open(SettingsEditorPage.Colors)
        s.push(SettingsEditorPage.ColorPick(ColorField.DAY_BACKGROUND))
        s.close()
        assertTrue(s.pages.value.isEmpty())
    }

    /** The vanished-row guard: when the settings state stops containing a row whose editor is open,
     *  the whole sheet closes rather than rendering a page with nothing behind it. */
    @Test fun closeIfClosesTheWholeSheetWhenAnyPageMatches() {
        val s = SettingsEditorStack()
        s.open(SettingsEditorPage.Row("FONTSIZE"))
        s.push(SettingsEditorPage.Colors)
        s.closeIf { it is SettingsEditorPage.Row && it.key == "FONTSIZE" }
        assertTrue(s.pages.value.isEmpty())
    }

    @Test fun closeIfLeavesTheStackAloneWhenNothingMatches() {
        val s = SettingsEditorStack()
        s.open(SettingsEditorPage.Row("FONTSIZE"))
        s.closeIf { it is SettingsEditorPage.Row && it.key == "TOPMARGIN" }
        assertEquals(1, s.depth)
    }
}
