package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTranslationsTest {
    @Test
    fun anIndexedActiveDocumentIsAppendedToTheSelection() {
        assertEquals(listOf("ESV", "KJV"), searchTranslationIds(listOf("ESV"), "KJV", activeIsIndexed = true))
    }

    @Test
    fun anAlreadySelectedActiveDocumentIsNotDuplicated() {
        assertEquals(listOf("ESV", "KJV"), searchTranslationIds(listOf("ESV", "KJV"), "KJV", activeIsIndexed = true))
    }

    @Test
    fun anUnindexedActiveDocumentIsNotAppended() {
        // Appending it would force an index build on top of a selection that already works.
        assertEquals(listOf("ESV"), searchTranslationIds(listOf("ESV"), "KJV", activeIsIndexed = false))
    }

    @Test
    fun anEmptySelectionFallsBackToTheActiveDocumentEvenWhenUnindexed() {
        // There is nothing else to search, so the index prompt must stay reachable.
        assertEquals(listOf("KJV"), searchTranslationIds(emptyList(), "KJV", activeIsIndexed = false))
    }
}
