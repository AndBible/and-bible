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
package net.bible.service.sword

import android.util.Log
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.VerseKey
import java.util.*

/** Record which documents have bad xml, normally in first or last chapters and use slightly slower JSword parser with error recovery
 * we have a fast way of handling OSIS zText docs but the following need the superior JSword error recovery for mismatching tags
 * FreCrampon
 * AB
 * FarsiOPV
 * Afr1953
 * UKJV
 * WEB
 * HNV
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentParseMethod {
    private enum class FailPosition {
        NONE,  //								FIRST_BIBLE_CHAPTER, LAST_BIBLE_CHAPTER, FIRST_AND_LAST_BIBLE_CHAPTER,
        //								FIRST_BOOK_CHAPTER,  LAST_BOOK_CHAPTER,
        FIRST_AND_LAST_BOOK_CHAPTER,
        ALL
    }

    private val failureInfoMap: MutableMap<String, FailPosition> = HashMap()
    /** return true if this book's chapter is believed to have a good xml structure and not require recovery fallback
     */
    fun isFastParseOkay(document: Book, key: Key): Boolean {
        var isFastParseOkay = false
        val documentFailPosition = failureInfoMap[document.initials]
        isFastParseOkay = if (documentFailPosition == null) {
            true
        } else {
            when (documentFailPosition) {
                FailPosition.NONE ->  // should never come here
                    true
                FailPosition.ALL -> false
                FailPosition.FIRST_AND_LAST_BOOK_CHAPTER -> !isStartOrEndOfBook(key)
            }
        }
        return isFastParseOkay
    }

    /** a document has bad xml structure so record the fact so the default fault tolerant parser is used in the future
     * many books have extra tags in first and/or last chapters hence the graded level of failures
     */
    fun failedToParse(document: Book, key: Key) {
        val initials = document.initials
        var documentFailPosition = failureInfoMap[initials]
        documentFailPosition = if (isStartOrEndOfBook(key)) {
            FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
        } else {
            FailPosition.ALL
        }
        failureInfoMap[initials] = documentFailPosition
    }

    private fun isStartOrEndOfBook(key: Key): Boolean {
        var isStartOrEnd = false
        try {
            if (key is VerseKey<*>) {
                val verse = KeyUtil.getVerse(key)
                val chapter = verse.chapter
                val book = verse.book
                isStartOrEnd = chapter == 1 || chapter == verse.versification.getLastChapter(book)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verse error", e)
            isStartOrEnd = false
        }
        return isStartOrEnd
    }

    companion object {
        private const val TAG = "DocumentParseMethod"
    }

    init {
        failureInfoMap["FreCrampon"] = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
        failureInfoMap["AB"] = FailPosition.ALL
        failureInfoMap["FarsiOPV"] = FailPosition.ALL
        //Afr1953 only has trouble with Gen 1 and Rev 22
        failureInfoMap["Afr1953"] = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
        failureInfoMap["UKJV"] = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
        //WEB has trouble with Gen 1 and Rev 22, is okay for much of NT but books like Hosea are also misformed
        failureInfoMap["WEB"] = FailPosition.ALL
        //HNV only has trouble with Rev 22
        failureInfoMap["HNV"] = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
        failureInfoMap["BulVeren"] = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
        failureInfoMap["BulCarigradNT"] = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER
    }
}
