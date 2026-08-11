package net.bible.sharedcore.search

/** The document categories search has to distinguish. Mirrors JSword's `BookCategory` subset in use. */
enum class SearchDocumentCategory { BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK }

/** Everything `searchKindFor` needs about a document, so the decision stays pure and host-testable. */
data class SearchDocumentInfo(
    val docId: String,
    val category: SearchDocumentCategory,
    val isEpub: Boolean,
    val indexDone: Boolean,
)

/** What kind of search (if any) a document supports. */
sealed interface SearchKind {
    data class Bible(val docId: String) : SearchKind
    data class Epub(val docId: String) : SearchKind
    data class NeedsIndex(val docId: String, val forEpub: Boolean) : SearchKind
    data object Unavailable : SearchKind
}

/**
 * The single place a document type decides how search behaves. Replaces the intent branches of
 * `SearchControl.getSearchIntent`, and fixes two defects that lived in them:
 *
 * 1. An unindexed EPUB is a `GENERAL_BOOK`, so it fell into the `GENERAL_BOOK -> null` branch and
 *    search silently did nothing — making issue #3093's index prompt unreachable from the reading view.
 *    So `isEpub` is tested BEFORE the general-book guard.
 * 2. Dictionaries and non-EPUB general books were admitted to the Lucene path, but `candidateBibles()`
 *    offers only Bibles and `getMultiSearchResults` keeps only `Verse` keys — zero hits, no message.
 *    They are `Unavailable`, which the host reports honestly.
 */
fun searchKindFor(doc: SearchDocumentInfo?): SearchKind {
    if (doc == null) return SearchKind.Unavailable
    if (doc.isEpub) {
        return if (doc.indexDone) SearchKind.Epub(doc.docId)
        else SearchKind.NeedsIndex(doc.docId, forEpub = true)
    }
    return when (doc.category) {
        SearchDocumentCategory.BIBLE, SearchDocumentCategory.COMMENTARY ->
            if (doc.indexDone) SearchKind.Bible(doc.docId)
            else SearchKind.NeedsIndex(doc.docId, forEpub = false)
        SearchDocumentCategory.DICTIONARY, SearchDocumentCategory.GENERAL_BOOK ->
            SearchKind.Unavailable
    }
}
