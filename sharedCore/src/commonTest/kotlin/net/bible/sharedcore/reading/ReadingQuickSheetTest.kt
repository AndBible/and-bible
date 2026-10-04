package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import net.bible.sharedcore.navigation.DocumentSheetScope

class ReadingQuickSheetTest {
    @Test fun theSixQuickSheetsAreDistinct() {
        val all = listOf(
            ReadingQuickSheet.History,
            ReadingQuickSheet.Workspaces,
            ReadingQuickSheet.Documents(),
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid),
            ReadingQuickSheet.Share(ShareVersesInput(
                verses = emptyList(), startOffset = null, endOffset = null,
                referenceAbbreviated = "", referenceFull = "", versionAbbreviation = "",
                notesText = null, advertiseText = "", hasRange = false,
            )),
            ReadingQuickSheet.ReadHistory(bookId = "GEN", chapter = 1),
        )
        assertEquals(all.size, all.toSet().size, "each quick sheet must be its own value")
    }

    @Test fun readHistorySheetsAreDistinctByBookAndChapter() {
        assertNotEquals(
            ReadingQuickSheet.ReadHistory(bookId = "GEN", chapter = 1),
            ReadingQuickSheet.ReadHistory(bookId = "GEN", chapter = 2),
        )
        assertNotEquals(
            ReadingQuickSheet.ReadHistory(bookId = "GEN", chapter = 1),
            ReadingQuickSheet.ReadHistory(bookId = "EXOD", chapter = 1),
        )
    }

    @Test fun keyChooserKindsAreDistinctFromEachOther() {
        assertNotEquals(
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid),
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Map),
        )
        assertEquals(3, KeyChooserKind.entries.size, "Grid, Map, GeneralBook — spec §4.6")
    }

    @Test fun documentSheetsAreDistinctByScope() {
        assertNotEquals<ReadingQuickSheet>(
            ReadingQuickSheet.Documents(DocumentSheetScope.BIBLE),
            ReadingQuickSheet.Documents(DocumentSheetScope.COMMENTARY),
        )
        assertEquals<ReadingQuickSheet>(ReadingQuickSheet.Documents(), ReadingQuickSheet.Documents(DocumentSheetScope.ALL))
    }
}
