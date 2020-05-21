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
package net.bible.service.format.osistohtml.taghandler

import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import java.util.*
import kotlin.collections.HashMap

/** Display an img if the current verse has MyNote
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class BookmarkMarker(private val parameters: OsisToHtmlParameters?, private val verseInfo: VerseInfo) {
    private var bookmarkStylesByBookmarkedVerse: Map<Int, Set<BookmarkStyle>>
        = parameters?.bookmarkStylesByBookmarkedVerse ?: HashMap()

    /** Get any bookmark classes for current verse
     */
    open val bookmarkClasses: List<String>
        get() {
            if (parameters?.isShowBookmarks == true) {
                if (bookmarkStylesByBookmarkedVerse.containsKey(verseInfo.currentVerseNo)) {
                    val bookmarkStyles = bookmarkStylesByBookmarkedVerse.get(verseInfo.currentVerseNo)!!
                    return getStyleNames(bookmarkStyles)
                }
            }
            return emptyList()
        }

    private fun getStyleNames(bookmarkStyles: Set<BookmarkStyle>): List<String> {
        val styleNames: MutableList<String> = ArrayList()
        for (bookmarkStyle in bookmarkStyles) {
            styleNames.add(bookmarkStyle.name)
        }
        return styleNames
    }
}
