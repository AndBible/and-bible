package net.bible.service.sword

import org.crosswire.jsword.passage.VerseRange

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
