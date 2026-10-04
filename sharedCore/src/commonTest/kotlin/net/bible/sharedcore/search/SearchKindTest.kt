package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchKindTest {

    private fun doc(
        category: SearchDocumentCategory,
        isEpub: Boolean = false,
        indexDone: Boolean = true,
    ) = SearchDocumentInfo("D", category, isEpub, indexDone)

    @Test
    fun anIndexedBibleSearchesAsABible() {
        assertEquals(SearchKind.Bible("D"), searchKindFor(doc(SearchDocumentCategory.BIBLE)))
    }

    @Test
    fun anIndexedCommentarySearchesAsABible() {
        // Commentaries carry Verse keys, which is the only thing getMultiSearchResults accepts.
        assertEquals(SearchKind.Bible("D"), searchKindFor(doc(SearchDocumentCategory.COMMENTARY)))
    }

    @Test
    fun anIndexedEpubSearchesAsAnEpub() {
        assertEquals(
            SearchKind.Epub("D"),
            searchKindFor(doc(SearchDocumentCategory.GENERAL_BOOK, isEpub = true)),
        )
    }

    @Test
    fun anUnindexedEpubOffersIndexingInsteadOfBeingSilentlyUnavailable() {
        // Defect 1: EPUB is a GENERAL_BOOK, so the old GENERAL_BOOK -> null branch swallowed it and
        // issue #3093's "ask to create index" path was unreachable from the reading view.
        assertEquals(
            SearchKind.NeedsIndex("D", forEpub = true),
            searchKindFor(doc(SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = false)),
        )
    }

    @Test
    fun anUnindexedBibleOffersIndexing() {
        assertEquals(
            SearchKind.NeedsIndex("D", forEpub = false),
            searchKindFor(doc(SearchDocumentCategory.BIBLE, indexDone = false)),
        )
    }

    @Test
    fun aDictionaryIsUnavailableRatherThanSilentlyReturningNothing() {
        // Defect 2: the old indexed-non-EPUB branch admitted dictionaries, but candidateBibles()
        // offers only Bibles and getMultiSearchResults accepts only Verse keys, so the search
        // returned zero hits with no explanation.
        assertEquals(SearchKind.Unavailable, searchKindFor(doc(SearchDocumentCategory.DICTIONARY)))
    }

    @Test
    fun aNonEpubGeneralBookIsUnavailable() {
        assertEquals(SearchKind.Unavailable, searchKindFor(doc(SearchDocumentCategory.GENERAL_BOOK)))
    }

    @Test
    fun noDocumentIsUnavailable() {
        assertEquals(SearchKind.Unavailable, searchKindFor(null))
    }
}
