package net.bible.sharedcore.platform

/** Strings domain code needs. One member per resource; grows per batch (spec L1a section 3). */
interface CoreStrings {
    val labelAll: String // R.string.all
    val errorOccurred: String // R.string.error_occurred
    fun somethingWithParenthesis(a: String, b: String): String // R.string.something_with_parenthesis: "%1$s (%2$s)"
    fun readingPlanDay(day: String): String // R.string.rdg_plan_day: "Day %s"
    val sortByAlphabetical: String // R.string.sort_by_alphabetical
    val sortByBibleBook: String // R.string.sort_by_bible_book
    fun documentNotInstalled(initials: String): String // R.string.document_not_installed: "Please download '%s'"
    val noIndexedBibleWithStrongsRef: String // R.string.no_indexed_bible_with_strongs_ref
    val wordNotFoundInDictionaries: String // R.string.word_not_found_in_dictionaries
}
