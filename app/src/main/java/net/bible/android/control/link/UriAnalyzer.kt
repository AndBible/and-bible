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
package net.bible.android.control.link

import android.net.Uri
import android.util.Log
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import java.net.URI

class UriAnalyzer {
    companion object {
        const val OSIS_PROTOCOL = "osis" //$NON-NLS-1$
        const val CONTENT_PROTOCOL = "content" //$NON-NLS-1$

        const val SWORD_PROTOCOL = "sword" //$NON-NLS-1$
        const val BIBLE_PROTOCOL = "bible" //$NON-NLS-1$
        const val GREEK_DEF_PROTOCOL = "gdef" //$NON-NLS-1$
        const val HEBREW_DEF_PROTOCOL = "hdef" //$NON-NLS-1$
        const val NOTE_PROTOCOL = "note"
        const val MYNOTE_PROTOCOL = "mynote"
        const val ROBINSON_GREEK_MORPH_PROTOCOL = "robinson" //$NON-NLS-1$
        const val STRONG_PROTOCOL = "strong" //$NON-NLS-1$
    }

    enum class DocType {
        BIBLE, GREEK_DIC, HEBREW_DIC, ROBINSON, ALL_GREEK, ALL_HEBREW, SPECIFIC_DOC, NOTE, MYNOTE
    }

    var docType = DocType.BIBLE
        private set
    var book: String? = null
        private set
    var key = ""
        private set

	var protocol = ""
		private set

	fun analyze(urlStr: String): Boolean {
        // check for urls like gdef:01234
        // split the prefix from the book
        var ref = if (!urlStr.contains(":")) {
            protocol = BIBLE_PROTOCOL
            urlStr
        } else {
            val uriTokens = urlStr.split(":", limit=2)
            protocol = uriTokens[0]
            uriTokens[1]
        }
        docType = when (protocol) {
            SWORD_PROTOCOL -> DocType.SPECIFIC_DOC
            OSIS_PROTOCOL -> DocType.BIBLE
            CONTENT_PROTOCOL -> DocType.BIBLE
            BIBLE_PROTOCOL -> DocType.BIBLE
            GREEK_DEF_PROTOCOL -> DocType.GREEK_DIC
            HEBREW_DEF_PROTOCOL -> DocType.HEBREW_DIC
            ROBINSON_GREEK_MORPH_PROTOCOL -> DocType.ROBINSON
            STRONG_PROTOCOL -> {
                val firstLetter = ref.first()
                if (firstLetter == 'G') {
                    DocType.GREEK_DIC
                } else {
                    DocType.HEBREW_DIC
                }
            }
            NOTE_PROTOCOL -> DocType.NOTE
            MYNOTE_PROTOCOL -> DocType.MYNOTE
            else -> return false
        }

        // Document
        if (StringUtils.isEmpty(ref)) {
            return false
        }
        // remove the first 2 slashes from the url e.g. //module/key
        ref = StringUtils.strip(ref, "/")
        if (!ref.contains("/")) {
            try {
                val (bookCandidateStr, refCandidateStr) = ref.split(".", limit = 2)
                val bookCandidate = try {Books.installed().getBook(bookCandidateStr)} catch (e: Exception) {null}
                if (bookCandidate?.bookCategory != null && bookCandidate.bookCategory != BookCategory.BIBLE) {
                    docType = DocType.SPECIFIC_DOC
                    key = refCandidateStr
                    book = bookCandidateStr
                } else {
                    key = ref
                }
            } catch(e: Exception) {
                Log.w("UriAnalyzer", "Error in parsing $urlStr", e)
                key = ref
            }
        } else {
            val firstSlash = ref.indexOf("/")
            book = ref.substring(0, firstSlash)
            // handle uri like sword://Bible/John.17.11 found in Calvin's commentary avoiding any attempt to find a book named Bible that will fail
            if (BIBLE_PROTOCOL.equals(book, ignoreCase = true)) {
                docType = DocType.BIBLE
            }
            // safe to grab after slash because slash can't be on end due to above strip("/")
            key = ref.substring(firstSlash + 1)
        }
        // handled this url (or at least attempted to)
        return true
    }

}
