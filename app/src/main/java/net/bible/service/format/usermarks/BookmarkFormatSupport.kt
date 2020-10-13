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
package net.bible.service.format.usermarks

import net.bible.android.control.ApplicationScope
import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.db.bookmark.BookmarkDBAdapter
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import java.util.*
import javax.inject.Inject

/**
 * Support display of bookmarked verses.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class BookmarkFormatSupport @Inject constructor() {
    fun getVerseBookmarkStylesInPassage(passage: Key): Map<Int, MutableSet<BookmarkStyle>> {
        // assumes the passage only covers one book, which always happens to be the case here
        val firstVerse = KeyUtil.getVerse(passage)
        val book = firstVerse.book

        // get all Bookmarks in containing book to include variations due to differing versifications
        val db = BookmarkDBAdapter()
        val bookmarkList: List<BookmarkDto>
        val defaultBookmarkStyle = BookmarkStyle.valueOf(sharedPreferences.getString(
            "default_bookmark_style_pref", BookmarkStyle.YELLOW_STAR.name)!!)
        val bookmarkStylesByVerseNoInPassage: MutableMap<Int, MutableSet<BookmarkStyle>> = HashMap()
        try {
            bookmarkList = db.getBookmarksInBook(book)

            // convert to required versification and check verse is in passage
            if (bookmarkList != null) {
                val requiredVersification = firstVerse.versification
                for (bookmarkDto in bookmarkList) {
                    val bookmarkVerseRange = bookmarkDto.getVerseRange(requiredVersification)
                    if (passage.contains(bookmarkVerseRange.start)) {
                        val bookmarkLabels = db.getBookmarkLabels(bookmarkDto).toMutableList()
                        if (bookmarkLabels.isEmpty()) {
                            bookmarkLabels.add(LabelDto(null, null, defaultBookmarkStyle))
                        }
                        val bookmarkStyles = getBookmarkStyles(bookmarkLabels, defaultBookmarkStyle)
                        for (verse in bookmarkVerseRange.toVerseArray()) {
                            var stylesSet = bookmarkStylesByVerseNoInPassage[verse.verse]
                            if (stylesSet != null) {
                                stylesSet.addAll(bookmarkStyles)
                            } else {
                                stylesSet = TreeSet(bookmarkStyles)
                                bookmarkStylesByVerseNoInPassage[verse.verse] = stylesSet
                            }
                        }
                    }
                }
            }
        } finally {
        }
        return bookmarkStylesByVerseNoInPassage
    }

    /**
     * Get distinct styles in enum order
     */
    private fun getBookmarkStyles(bookmarkLabels: List<LabelDto>, defaultStyle: BookmarkStyle): List<BookmarkStyle> {
        val bookmarkStyles: MutableSet<BookmarkStyle> = TreeSet()
        for (label in bookmarkLabels) {
            val style = label.bookmarkStyle
            if (style != null) {
                bookmarkStyles.add(style)
            } else {
                bookmarkStyles.add(defaultStyle)
            }
        }
        return ArrayList(bookmarkStyles)
    }
}
