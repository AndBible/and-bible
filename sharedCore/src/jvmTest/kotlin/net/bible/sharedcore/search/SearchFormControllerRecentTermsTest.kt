package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFormControllerRecentTermsTest {
    private fun controller(
        initial: List<String> = emptyList(),
        persisted: MutableList<List<String>> = mutableListOf(),
    ) = SearchFormController(
        currentBookName = "",
        persistTranslations = {},
        persistRecentTerms = { persisted.add(it) },
        loadRecentTerms = { initial },
    )

    @Test fun seedsFromLoad() {
        val c = controller(initial = listOf("grace", "love"))
        assertEquals(listOf("grace", "love"), c.recentTerms.value)
    }

    @Test fun recordPrependsMostRecent() {
        val c = controller(initial = listOf("grace"))
        c.recordRecentTerm("love")
        assertEquals(listOf("love", "grace"), c.recentTerms.value)
    }

    @Test fun recordDeduplicatesMovingToFront() {
        val c = controller(initial = listOf("grace", "love"))
        c.recordRecentTerm("grace")
        assertEquals(listOf("grace", "love"), c.recentTerms.value)
    }

    @Test fun recordCapsAtTen() {
        val c = controller(initial = (1..10).map { "t$it" })
        c.recordRecentTerm("new")
        assertEquals(11 - 1, c.recentTerms.value.size)
        assertEquals("new", c.recentTerms.value.first())
        assertEquals("t9", c.recentTerms.value.last()) // t10 dropped
    }

    @Test fun blankIsIgnoredAndTermTrimmed() {
        val c = controller()
        c.recordRecentTerm("   ")
        assertEquals(emptyList(), c.recentTerms.value)
        c.recordRecentTerm("  grace  ")
        assertEquals(listOf("grace"), c.recentTerms.value)
    }

    @Test fun recordPersists() {
        val persisted = mutableListOf<List<String>>()
        val c = controller(persisted = persisted)
        c.recordRecentTerm("grace")
        assertEquals(listOf(listOf("grace")), persisted)
    }
}
