package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EpubResultRowTest {
    private fun row(keyId: String, ordinal: Int) =
        EpubResultRow(keyId, ordinal, "Chapter 3", StyledText(listOf(StyledRun("hit"))))

    @Test
    fun twoHitsInTheSameFragmentHaveDistinctRowIds() {
        val a = row("Epub-book:3", 11)
        val b = row("Epub-book:3", 12)
        assertNotEquals(a.rowId, b.rowId)
    }

    @Test
    fun rowIdIsStableForTheSameHit() {
        assertEquals(row("Epub-book:3", 11).rowId, row("Epub-book:3", 11).rowId)
    }
}
