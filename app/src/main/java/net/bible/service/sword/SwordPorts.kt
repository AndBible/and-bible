package net.bible.service.sword

import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.book.Book
import okio.Source

/** The document and offsets needed to share or copy a selection, without a view dependency. */
interface ShareSelection {
    val bookInitials: String?
    val verseRange: VerseRange?
    val startOffset: Int?
    val endOffset: Int?
    val notes: String?
}

/** Retains the platform's HTML rendering when converting a note to plain text. */
fun interface HtmlToPlainText {
    fun convert(html: String): String
}

fun isRtlLanguage(language: String?): Boolean = language in setOf(
    "ar", "fa", "he", "iw", "ur", "ps", "sd", "ug", "yi", "ji", "dv", "ckb", "syr", "ks", "lrc", "mzn",
)

/** A caller-owned unlock dialog; the sword domain does not retain an Activity. */
fun interface BookUnlocker {
    suspend fun unlock(book: Book): Boolean
}

/** Metadata and a caller-owned stream for a picked document. The importer closes the stream. */
interface ContentSource {
    fun openSource(uri: String): Source?
    fun displayName(uri: String): String?
    fun mimeType(uri: String): String?
}
