/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.search


fun prepareSearchTerms(searchTerms_: String): String {
    // Replaces strong:g00123 with REGEX strong:g*123. This is needed because the search term submitted by the 'Find all occcurrences includes extra zeros
    // The capitalisation is not important since we do a case insensitive search
    var searchTerms = searchTerms_
    if (searchTerms.contains("strong:")) {
        searchTerms = searchTerms.replace("strong:g0*".toRegex(), "strong:g0*")
        searchTerms = searchTerms.replace("strong:h0*".toRegex(), "strong:h0*")
    }
    return searchTerms
}

val splitSearchTermsRegex = Regex("""\s+(?=(?:"(?:\\"|[^"])+"|[^"])+$)""")

// Split the search terms on space characters that are not enclosed in double quotes
// Eg: 'moses "burning bush"' -> "moses" and "burning bush"
fun splitSearchTerms(searchTerms: String): List<String> = splitSearchTermsRegex.split(searchTerms)

fun prepareSearchWord(searchWord_: String): String {
    // Need to clean up the search word itself before trying to find the searchWord in the text
    // Eg: '+"burning bush"' -> 'burning bush'
    var searchWord = searchWord_
    searchWord =
        searchWord.replace("\"", "") // Remove quotes which indicate phrase searches
    searchWord = searchWord.replace("+", "") // Remove + which indicates AND searches
    searchWord = searchWord.replace("?", "\\p{L}") // Handles any letter from any language
    if (searchWord.length > 0) {
        searchWord = if (searchWord.substring(searchWord.length - 1) == "*") {
            searchWord.replace("*", "")
        } else {
            searchWord.replace("*", "\b") // Match on a word boundary
        }
    }
    return searchWord
}
