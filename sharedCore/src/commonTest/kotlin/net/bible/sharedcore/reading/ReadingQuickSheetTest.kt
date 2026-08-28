package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReadingQuickSheetTest {
    @Test fun theFourQuickSheetsAreDistinct() {
        val all = listOf(
            ReadingQuickSheet.History,
            ReadingQuickSheet.Workspaces,
            ReadingQuickSheet.Documents,
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid),
        )
        assertEquals(all.size, all.toSet().size, "each quick sheet must be its own value")
    }

    @Test fun keyChooserKindsAreDistinctFromEachOther() {
        assertNotEquals(
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid),
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Map),
        )
        assertEquals(3, KeyChooserKind.entries.size, "Grid, Map, GeneralBook — spec §4.6")
    }
}
