package net.bible.sharedcore.search

enum class SearchType { ALL_WORDS, ANY_WORDS, PHRASE }
enum class SearchBibleSection { ALL, OLD_TESTAMENT, NEW_TESTAMENT, CURRENT_BOOK }

/** A styled span of result text. Host builds these from the OSIS/JDOM Strong's walk; :sharedUi → AnnotatedString. */
data class StyledRun(val text: String, val bold: Boolean = false, val highlight: Boolean = false)
data class StyledText(val runs: List<StyledRun>) {
    fun plainText(): String = runs.joinToString("") { it.text }
    companion object { fun plain(text: String) = StyledText(listOf(StyledRun(text))) }
}

data class SearchRequest(
    val query: String,
    val searchType: SearchType,
    val bibleSection: SearchBibleSection,
    val translationIds: List<String>,   // Book.initials; addressing key, never a list index
    val currentBookName: String,
    val isStrongsSearch: Boolean = false,
)

/** A selectable Bible for the results document selector. [id] is a Book.initials string (addressing key, never an index). */
data class BibleOption(val id: String, val abbreviation: String, val hasStrongs: Boolean)

data class TranslationMatchVd(val translationId: String, val abbreviation: String, val preview: StyledText)
data class SwordResultRow(
    val referenceName: String,
    val matches: List<TranslationMatchVd>,   // >1 → expandable; 1 → collapsed
    val primaryPreview: StyledText,
)
data class MultiSearchResults(
    val main: List<SwordResultRow>,
    val other: List<SwordResultRow>,
    val total: Int,
) {
    companion object { val EMPTY = MultiSearchResults(emptyList(), emptyList(), 0) }
}
