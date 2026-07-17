package net.bible.sharedcore.search
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateDocumentsTest {
    private val kjv = BibleOption(id = "KJV", abbreviation = "KJV", hasStrongs = true)
    private val esv = BibleOption(id = "ESV", abbreviation = "ESV", hasStrongs = false)
    private val web = BibleOption(id = "WEB", abbreviation = "WEB", hasStrongs = true)
    private val all = listOf(kjv, esv, web)

    @Test fun strongsSearch_keeps_only_hasStrongs() {
        assertEquals(listOf(kjv, web), candidateDocuments(strongsSearch = true, all = all))
    }

    @Test fun nonStrongsSearch_returns_all() {
        assertEquals(all, candidateDocuments(strongsSearch = false, all = all))
    }

    @Test fun empty_input_yields_empty() {
        assertEquals(emptyList(), candidateDocuments(strongsSearch = true, all = emptyList()))
        assertEquals(emptyList(), candidateDocuments(strongsSearch = false, all = emptyList()))
    }

    @Test fun order_is_preserved() {
        val ordered = listOf(web, kjv) // both hasStrongs, reversed input order
        assertEquals(ordered, candidateDocuments(strongsSearch = true, all = ordered))
        assertEquals(ordered, candidateDocuments(strongsSearch = false, all = ordered))
    }
}
