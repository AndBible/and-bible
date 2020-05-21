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

import net.bible.service.common.Logger
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo

/**
 * Write characters out to a StringBuilder - used while creating html for display
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class HtmlTextWriter {
    private val writer: StringBuilder
    private var dontWriteRequestCount = 0
    private var writeTempStoreRequestCount = 0
    private val tempStore = StringBuilder()

    // Prevent multiple conflicting preverse attempts
    private var insertionRequestCount = 0

    // allow insert at a certain position
    private var overwrittenString = ""
    fun write(htmlText: String?): Boolean {
        if (dontWriteRequestCount > 0) {
            // ignore all text
            return false
        } else if (writeTempStoreRequestCount == 0) {
            writer.append(htmlText)
        } else {
            tempStore.append(htmlText)
        }
        return true
    }

    /** allow line breaks and titles to be moved before verse number
     */
    fun writeOptionallyBeforeVerse(s: String?, verseInfo: VerseInfo) {
        val writeBeforeVerse = !verseInfo.isTextSinceVerse
        if (writeBeforeVerse) {
            beginInsertAt(verseInfo.positionToInsertBeforeVerse)
        }
        write(s)
        if (writeBeforeVerse) {
            finishInserting()
        }
    }

    /** allow pre-verse headings
     */
    fun beginInsertAt(insertOffset: Int) {
        insertionRequestCount++
        if (insertionRequestCount == 1) {
            overwrittenString = writer.substring(insertOffset)
            writer.delete(insertOffset, writer.length)
        }
    }

    /** finish inserting and restore overwritten tail of string
     */
    fun finishInserting() {
        if (insertionRequestCount == 1) {
            writer.append(overwrittenString)
            overwrittenString = ""
        }
        insertionRequestCount--
    }

    fun abortAnyUnterminatedInsertion() {
        if (insertionRequestCount > 0) {
            // force insertion to finish in the case a closing pre-verse tag was missing
            insertionRequestCount = 1
            finishInserting()
        }
    }

    val position: Int
        get() = writer.length

    fun removeAfter(position: Int) {
        writer.delete(position, writer.length)
    }

    fun reset() {
        writer.setLength(0)
    }

    fun writeToTempStore() {
        writeTempStoreRequestCount++
    }

    fun finishWritingToTempStore() {
        writeTempStoreRequestCount--
    }

    fun clearTempStore() {
        tempStore.delete(0, tempStore.length)
    }

    val tempStoreString: String
        get() = tempStore.toString()

    val html: String
        get() = writer.toString()

    fun setDontWrite(dontWrite: Boolean) {
        if (dontWrite) {
            dontWriteRequestCount++
        } else {
            dontWriteRequestCount--
        }
    }

    companion object {
        private val log = Logger("HtmlTextWriter")
    }

    init {
        writer = StringBuilder()
    }
}
