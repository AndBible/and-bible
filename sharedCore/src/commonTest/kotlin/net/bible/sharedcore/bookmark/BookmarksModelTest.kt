package net.bible.sharedcore.bookmark

import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookmarksModelTest {

    @Test fun sortMode_next_cycles_verbatim_classic_order() {
        assertEquals(BookmarkSortMode.BIBLE_ORDER_DESC, BookmarkSortMode.BIBLE_ORDER.next())
        assertEquals(BookmarkSortMode.CREATED_AT_DESC, BookmarkSortMode.BIBLE_ORDER_DESC.next())
        assertEquals(BookmarkSortMode.CREATED_AT, BookmarkSortMode.CREATED_AT_DESC.next())
        assertEquals(BookmarkSortMode.BIBLE_ORDER, BookmarkSortMode.CREATED_AT.next())
    }

    @Test fun sortMode_isBibleOrder_true_only_for_bible_order_states() {
        assertTrue(BookmarkSortMode.BIBLE_ORDER.isBibleOrder)
        assertTrue(BookmarkSortMode.BIBLE_ORDER_DESC.isBibleOrder)
        assertFalse(BookmarkSortMode.CREATED_AT_DESC.isBibleOrder)
        assertFalse(BookmarkSortMode.CREATED_AT.isBibleOrder)
    }

    @Test fun bookmarkFilterLabel_carries_fields() {
        val row = BookmarkFilterLabel(index = 1, displayName = "Unlabeled")
        assertEquals(1, row.index)
        assertEquals("Unlabeled", row.displayName)
    }

    @Test fun bookmarkRow_carries_all_fields() {
        val content = StyledText(listOf(StyledRun("start "), StyledRun("selected", bold = true), StyledRun(" end")))
        val notes = StyledText.plain("a note")
        val row = BookmarkRow(
            id = "B1",
            title = "Gen 1:1",
            dateText = "Sat, 2026-07-18 12:00",
            content = content,
            notes = notes,
            labelColors = listOf(-0xff0100, -0x1),
            isSpeak = false,
        )
        assertEquals("B1", row.id)
        assertEquals("Gen 1:1", row.title)
        assertEquals("Sat, 2026-07-18 12:00", row.dateText)
        assertEquals(content, row.content)
        assertEquals(notes, row.notes)
        assertEquals(listOf(-0xff0100, -0x1), row.labelColors)
        assertFalse(row.isSpeak)
    }

    @Test fun bookmarkRow_notes_may_be_null() {
        val row = BookmarkRow(
            id = "B2",
            title = "Ps 23:1",
            dateText = "Sat, 2026-07-18 12:00",
            content = StyledText.plain("text"),
            notes = null,
            labelColors = emptyList(),
            isSpeak = true,
        )
        assertNull(row.notes)
        assertTrue(row.isSpeak)
    }
}
