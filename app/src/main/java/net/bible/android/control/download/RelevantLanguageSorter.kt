/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.control.download

import org.apache.commons.lang3.ObjectUtils
import org.crosswire.common.util.Language
import org.crosswire.jsword.book.Book
import java.util.*

class RelevantLanguageSorter(installedDocuments: List<Book>) : Comparator<Language?> {
    private val relevantLanguages: MutableSet<String>

    /**
     * Compare languages, most popular first.
     *
     * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
     */
    override fun compare(lhs: Language?, rhs: Language?): Int {
        val lhsRelevant = isRelevant(lhs)
        val rhsRelevant = isRelevant(rhs)
        return if (lhsRelevant != rhsRelevant) {
            if (lhsRelevant) -1 else 1
        } else {
            ObjectUtils.compare(lhs, rhs)
        }
    }

    private fun isRelevant(lang: Language?): Boolean {
        return if (lang == null) {
            false
        } else relevantLanguages.contains(lang.code)
    }

    companion object {
        fun sort(languageList: MutableList<Language>, books: List<Book>) {
            val sorter = RelevantLanguageSorter(books)
            languageList.sortWith(compareBy(
                {!sorter.relevantLanguages.contains(it.code)},
                {it.name}
            ))
        }

        private val MAJOR_LANGUAGE_CODES = arrayOf("en", "de", "fr", "grc", "he", "ru", "ar", "zh", "pt")
    }

    init {
        relevantLanguages = HashSet()
        val defaultLanguageCode = Locale.getDefault().language
        relevantLanguages.add(defaultLanguageCode)
        relevantLanguages.addAll(Arrays.asList(*MAJOR_LANGUAGE_CODES))
        for (doc in installedDocuments) {
            val lang = doc.language
            if (lang != null) {
                relevantLanguages.add(lang.code)
            }
        }
    }
}
