package net.bible.sharedcore.bookmark

import net.bible.sharedcore.search.StyledText   // Batch-5 run-model (text + runs of bold/highlight)

/** One row of the bookmark list — plain, portable. [id] = IdType.toString(). */
data class BookmarkRow(
    val id: String,
    val title: String,              // verse name ("Gen 1:1") or generic key name
    val dateText: String,           // preformatted "EEE, yyyy-MM-dd HH:mm"
    val content: StyledText,        // startText + <b>text</b> + endText → bold run over the selection
    val notes: StyledText?,         // htmlToSpan(notes) → StyledText, or null (only shown when showNotes)
    val labelColors: List<Int>,     // ARGB chips (one per non-speak label; unlabelled → the Unlabeled color)
    val isSpeak: Boolean,
)

/** A filter-dropdown entry. [index] is the classic spinner position (0=All, 1=Unlabeled, …). */
data class BookmarkFilterLabel(val index: Int, val displayName: String)

/** The 4-state sort cycle actually reachable from the toggle (classic BookmarkSortOrder subset). */
enum class BookmarkSortMode {
    BIBLE_ORDER, BIBLE_ORDER_DESC, CREATED_AT_DESC, CREATED_AT;
    fun next(): BookmarkSortMode = when (this) {
        BIBLE_ORDER -> BIBLE_ORDER_DESC
        BIBLE_ORDER_DESC -> CREATED_AT_DESC
        CREATED_AT_DESC -> CREATED_AT
        CREATED_AT -> BIBLE_ORDER
    }
    val isBibleOrder: Boolean get() = this == BIBLE_ORDER || this == BIBLE_ORDER_DESC
}
