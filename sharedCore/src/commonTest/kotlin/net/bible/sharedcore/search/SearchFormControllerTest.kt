package net.bible.sharedcore.search

import kotlin.test.*

class SearchFormControllerTest {
    private fun c() = SearchFormController(currentBookName = "Genesis", persistTranslations = {})

    @Test fun buildRequest_maps_all_options() {
        val c = c()
        c.setQuery("faith")
        c.setSearchType(SearchType.PHRASE)
        c.setBibleSection(SearchBibleSection.NEW_TESTAMENT)
        c.setTranslations(listOf("KJV", "ESV"))
        val r = c.buildRequest()
        assertEquals("faith", r.query)
        assertEquals(SearchType.PHRASE, r.searchType)
        assertEquals(SearchBibleSection.NEW_TESTAMENT, r.bibleSection)
        assertEquals(listOf("KJV", "ESV"), r.translationIds)
        assertEquals("Genesis", r.currentBookName)
    }

    @Test fun setTranslations_persists() {
        var saved: List<String>? = null
        val c = SearchFormController("Gen", persistTranslations = { saved = it })
        c.setTranslations(listOf("KJV"))
        assertEquals(listOf("KJV"), saved)
    }

    @Test fun defaults_are_all_words_and_all_section() {
        val r = c().buildRequest()
        assertEquals(SearchType.ALL_WORDS, r.searchType)
        assertEquals(SearchBibleSection.ALL, r.bibleSection)
    }
}
