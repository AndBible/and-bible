package net.bible.sharedcore.search

/**
 * Host seam for EPUB full-text search (raw SQLite FTS5 on Android; a future iOS impl would wrap the
 * native store). Kept out of the controllers so `:sharedCore` stays iOS-clean.
 */
interface EpubSearchService {
    /** True if the EPUB's FTS5 index exists (classic `doc.epubBackend?.state?.isIndexed == true`). */
    fun isIndexed(docId: String): Boolean

    /** Run [query] under [mode] against [docId]'s FTS5 index; rows carry pre-parsed highlighted text. */
    suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode): List<EpubResultRow>
}
