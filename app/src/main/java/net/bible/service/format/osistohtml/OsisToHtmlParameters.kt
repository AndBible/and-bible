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
package net.bible.service.format.osistohtml

import net.bible.android.control.bookmark.BookmarkStyle
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.net.URI

/**Parameters passed into the Osis to HTML converter
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OsisToHtmlParameters {
    var isShowChapterDivider = false
    var chapter: Int? = null
    var isAsFragment = false
    var languageCode = "en"
    var isLeftToRight = true
    var isShowTitles = true
    var isShowVerseNumbers = false
    var isVersePerline = false
    var isShowMyNotes = false
    var isShowBookmarks = false
    private var defaultBookmarkStyle = BookmarkStyle.YELLOW_STAR
    var isShowNotes = false
    var isAutoWrapUnwrappedRefsInNote = false

    // KeyUtil always returns a Verse even if it is only Gen 1:1
    // used as a basis if a reference has only chapter and no book
    var basisRef: Verse? = null

    fun setBasicRef(value: Key) {
        // KeyUtil always returns a Verse even if it is only Gen 1:1
        basisRef = KeyUtil.getVerse(value)
    }
    var documentVersification: Versification? = null
        get() = if (field != null) {
            field
        } else {
            Versifications.instance().getVersification(Versifications.DEFAULT_V11N)
        }
    var font: String? = null
    var cssClassForCustomFont: String? = null
    var isShowStrongs = false
    var isShowMorphology = false
    var isRedLetter = false
    var cssStylesheetList: List<String>? = null
    var extraFooter: String? = null
    var isConvertStrongsRefsToLinks = false
    var versesWithNotes: List<Verse>? = null
    var bookmarkStylesByBookmarkedVerse: Map<Int, Set<BookmarkStyle>>? = null
    var moduleBasePath: URI? = null
    var indentDepth = 2
    var isBible = false
    val cssStylesheets: String
        get() {
            val builder = StringBuilder()
            if (cssStylesheetList != null) {
                for (styleSheet in cssStylesheetList!!) {
                    builder.append(styleSheet)
                }
            }
            return builder.toString()
        }

    fun setDefaultBookmarkStyle(defaultBookmarkStyle: BookmarkStyle) {
        this.defaultBookmarkStyle = defaultBookmarkStyle
    }

}
