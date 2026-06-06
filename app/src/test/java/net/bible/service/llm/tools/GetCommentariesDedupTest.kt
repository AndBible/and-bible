/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.llm.tools

import net.bible.service.llm.tools.read.GetCommentariesTool.RenderedVerse
import net.bible.service.llm.tools.read.GetCommentariesTool.deduplicateConsecutiveBlocks
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [deduplicateConsecutiveBlocks], the consecutive-verse merging used by
 * [net.bible.service.llm.tools.read.GetCommentariesTool].
 *
 * These are plain JUnit tests (no Robolectric/SWORD) because the function operates on
 * already-rendered content.
 */
class GetCommentariesDedupTest {

    @Test
    fun mergesConsecutiveIdenticalContent() {
        val verses = listOf(
            RenderedVerse("Matt.5.1", "A"),
            RenderedVerse("Matt.5.2", "A"),
            RenderedVerse("Matt.5.3", "A"),
        )
        val blocks = deduplicateConsecutiveBlocks(verses)
        assertEquals(1, blocks.size)
        assertEquals("Matt.5.1", blocks[0].startVerseRef)
        assertEquals("Matt.5.3", blocks[0].endVerseRef)
        assertEquals("A", blocks[0].content)
    }

    @Test
    fun keepsDistinctContentSeparate() {
        val verses = listOf(
            RenderedVerse("Matt.5.1", "A"),
            RenderedVerse("Matt.5.2", "B"),
            RenderedVerse("Matt.5.3", "B"),
        )
        val blocks = deduplicateConsecutiveBlocks(verses)
        assertEquals(2, blocks.size)
        assertEquals("Matt.5.1", blocks[0].startVerseRef)
        assertEquals("Matt.5.1", blocks[0].endVerseRef)
        assertEquals("Matt.5.2", blocks[1].startVerseRef)
        assertEquals("Matt.5.3", blocks[1].endVerseRef)
    }

    /**
     * Regression test for OSTicket #3303: a "selected passages" commentary returns the
     * same large block for an entire passage spanning a chapter boundary. The rendered
     * content is identical for every verse, so it must collapse into a single block —
     * previously it leaked into two copies because dedup compared raw OSIS XML, which
     * differs in per-verse metadata across the chapter boundary.
     */
    @Test
    fun mergesIdenticalContentAcrossChapterBoundary() {
        val sharedText = "## Preface To The Prophets ... long shared commentary text"
        val verses = listOf(
            RenderedVerse("Isa.40.27", sharedText),
            RenderedVerse("Isa.40.28", sharedText),
            RenderedVerse("Isa.40.31", sharedText),
            RenderedVerse("Isa.41.1", sharedText),
            RenderedVerse("Isa.41.7", sharedText),
        )
        val blocks = deduplicateConsecutiveBlocks(verses)
        assertEquals(1, blocks.size)
        assertEquals("Isa.40.27", blocks[0].startVerseRef)
        assertEquals("Isa.41.7", blocks[0].endVerseRef)
    }

    @Test
    fun nullContentActsAsSeparatorAndIsDropped() {
        val verses = listOf(
            RenderedVerse("Matt.5.1", "A"),
            RenderedVerse("Matt.5.2", null),
            RenderedVerse("Matt.5.3", "A"),
        )
        val blocks = deduplicateConsecutiveBlocks(verses)
        // Same content either side of a gap stays as two blocks (the gap breaks the run).
        assertEquals(2, blocks.size)
        assertEquals("Matt.5.1", blocks[0].startVerseRef)
        assertEquals("Matt.5.1", blocks[0].endVerseRef)
        assertEquals("Matt.5.3", blocks[1].startVerseRef)
        assertEquals("Matt.5.3", blocks[1].endVerseRef)
    }

    @Test
    fun emptyInputProducesNoBlocks() {
        assertEquals(0, deduplicateConsecutiveBlocks(emptyList()).size)
    }

    @Test
    fun allNullProducesNoBlocks() {
        val verses = listOf(
            RenderedVerse("Matt.5.1", null),
            RenderedVerse("Matt.5.2", null),
        )
        assertEquals(0, deduplicateConsecutiveBlocks(verses).size)
    }
}
