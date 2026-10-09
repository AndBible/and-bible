package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.sharedcore.platform.CoreStrings

/** Reads the resource on every access so locale changes take effect. */
class AndroidCoreStrings(private val context: Context) : CoreStrings {
    override val labelAll: String get() = context.getString(R.string.all)
    override val errorOccurred: String get() = context.getString(R.string.error_occurred)
    override fun somethingWithParenthesis(a: String, b: String): String = context.getString(R.string.something_with_parenthesis, a, b)
    override fun readingPlanDay(day: String): String = context.getString(R.string.rdg_plan_day, day)
    override val sortByAlphabetical: String get() = context.getString(R.string.sort_by_alphabetical)
    override val sortByBibleBook: String get() = context.getString(R.string.sort_by_bible_book)
    override fun documentNotInstalled(initials: String): String = context.getString(R.string.document_not_installed, initials)
    override val noIndexedBibleWithStrongsRef: String get() = context.getString(R.string.no_indexed_bible_with_strongs_ref)
    override val wordNotFoundInDictionaries: String get() = context.getString(R.string.word_not_found_in_dictionaries)
}
