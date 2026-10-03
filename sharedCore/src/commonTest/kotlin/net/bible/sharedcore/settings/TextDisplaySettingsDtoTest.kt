package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TextDisplaySettingsDtoTest {
    @Test fun textSettingType_has35Entries() {
        assertEquals(35, TextSettingType.entries.size)
    }

    @Test fun textSettingType_namesAreStableForClassicTypesMapping() {
        // The impl maps 1:1 via Types.valueOf(name); guard the exact names/order.
        assertEquals("FONTSIZE", TextSettingType.entries.first().name)
        assertEquals("SHOW_READING_PROGRESS", TextSettingType.entries.last().name)
        assertEquals(true, TextSettingType.entries.map { it.name }.containsAll(
            listOf("COLORS", "BOOKMARKS_HIDELABELS", "PAGE_SCROLL_AMOUNT", "SCROLL_HELPER_LINE_STYLE")))
    }

    @Test fun rowValueEquality_holdsAndDiffersOnChange() {
        val a = TextSettingRow(TextSettingType.JUSTIFY, TextSettingRowValue.Bool(true), InheritedFrom.NONE, enabled = true, visible = true)
        val b = a.copy()
        assertEquals(a, b)
        assertNotEquals(a, a.copy(value = TextSettingRowValue.Bool(false)))
        assertNotEquals(a, a.copy(inheritedFrom = InheritedFrom.WORKSPACE))
    }

    @Test fun scopeIsValueType() {
        assertEquals(SettingsScope.Window("w1", "ws1"), SettingsScope.Window("w1", "ws1"))
        assertNotEquals(SettingsScope.Workspace("ws1") as SettingsScope, SettingsScope.Global)
    }
}
