/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.navigation

import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import java.util.Comparator
import java.util.regex.Pattern

/** Compare Bible book names alphabetically
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleBookAlphabeticalComparator(private val versification: Versification) :
    Comparator<BibleBook> {
    override fun compare(bibleBook1: BibleBook, bibleBook2: BibleBook): Int {
        return getSortableBoookName(bibleBook1).compareTo(getSortableBoookName(bibleBook2))
    }

    private fun getSortableBoookName(bibleBook: BibleBook): String {
        val name =
            versification.getShortName(bibleBook).lowercase(LocaleProviderManager.getLocale())
        // get the character name at the start eg '1 cor' -> 'cor1' so that books with a number at the start do not float to the top
        val bookName = NUMBERS_PATTERN.matcher(name).replaceAll("")
        val bookNumbers = NOT_NUMBERS_PATTERN.matcher(name).replaceAll("")
        return bookName + bookNumbers
    }

    companion object {
        private val NUMBERS_PATTERN = Pattern.compile("[0-9]")
        private val NOT_NUMBERS_PATTERN = Pattern.compile("[^0-9]")
    }
}
