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

import net.bible.service.common.Constants
import net.bible.service.common.Logger
import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes
import java.util.*

/**
 * Surround whole verse with
 * <span class='verse' id='N'><span class='verseNo'>N</span>verse text here</span>
 * Write the verse number at the beginning of a verse
 * Also handle verse per line
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class VerseHandler(private val parameters: OsisToHtmlParameters, private val verseInfo: VerseInfo, private val bookmarkMarker: BookmarkMarker, private val myNoteMarker: MyNoteMarker, private val writer: HtmlTextWriter) : OsisTagHandler {
    private var writerRollbackPosition = 0
    override val tagName: String
        get() = OSISUtil.OSIS_ELEMENT_VERSE

    override fun start(attrs: Attributes) {
        writerRollbackPosition = writer.position
        val verseNo = calculateVerseNumber(attrs, verseInfo.currentVerseNo)
        verseInfo.currentVerseNo = verseNo
        verseInfo.osisID = attrs.getValue(OSISUtil.OSIS_ATTR_OSISID)
        if (parameters.isVersePerline) {
            //close preceding verse
            if (verseInfo.currentVerseNo > 1) {
                writer.write("</div>")
            }
            // start current verse
            writer.write("<div>")
        }
        val classes: MutableList<String?> = ArrayList(1)
        classes.add("verse")
        classes.addAll(bookmarkMarker.bookmarkClasses)
        writeVerseStart(verseNo, classes)

        // initialise other related handlers that write content at start of verse
        myNoteMarker.start(attrs)

        // record that we are into a new verse
        verseInfo.isTextSinceVerse = false
    }

    override fun end() {
        // ensure any unclosed pre-verse tags do not cause invisible verses
        writer.abortAnyUnterminatedInsertion()

        // these related handlers currently do nothing on end
        myNoteMarker.end()
        if (verseInfo.isTextSinceVerse) {
            writeVerseEnd()
        } else {
            writer.removeAfter(writerRollbackPosition)
        }
    }

    private fun calculateVerseNumber(attrs: Attributes?, currentVerseNo: Int): Int {
        var verseNo: Int? = null
        if (attrs != null) {
            val osisId = attrs.getValue(OSISUtil.OSIS_ATTR_OSISID)
            if (StringUtils.isNotEmpty(osisId)) {
                verseNo = TagHandlerHelper.osisIdToVerseNum(osisId)
            }
        }
        if (verseNo == null) {
            verseNo = currentVerseNo + 1
        }
        return verseNo
    }

    private fun writeVerseStart(verseNo: Int, classList: List<String?>) {
        verseInfo.positionToInsertBeforeVerse = writer.position
        val cssClasses = StringUtils.join(classList, " ")

        // The id is used to 'jump to' the verse using javascript so always need the verse tag with an id
        // Do not show verse 0
        if (parameters.isBible) {
            val verseHtml = StringBuilder()
            verseHtml.append(" <span class='").append(cssClasses).append("' id='").append(getVerseId(verseNo)).append("'>").append(getVerseNumberHtml(verseNo))
            verseHtml.append("<span class='bookmark1'></span>")
            verseHtml.append("<span class='bookmark2'></span>")
            writer.write(verseHtml.toString())
        }
    }

    private fun getVerseId(verseNo: Int): String {
        val chapter = parameters.chapter
        return if (chapter != null) {
            "$chapter.$verseNo"
        } else {
            // verse without chapter does not make sense
            log.warn("No chapter specified for verse")
            ""
        }
    }

    private fun writeVerseEnd() {
        if(parameters.isBible) {
            writer.write("</span>")
        }
    }

    private fun getVerseNumberHtml(verseNo: Int): String {
        val verseNoSB = StringBuilder()
        if (parameters.isShowVerseNumbers && verseNo != 0) {
            verseNoSB.append("<span class='verseNo'>").append(verseNo).append("</span>").append(Constants.HTML.NBSP)
        } else {
            // We really want an empty span but that is illegal and causes problems such as incorrect verse calculation in Psalms
            // So use something that will hopefully interfere as little as possible - a zero-width-space
            // The verseNo class is required here because the default yellow-star positions itself after verseNo
            verseNoSB.append("<span class='verseNo position-marker'>").append(Constants.HTML.EMPTY_SPACE).append("</span>")
        }
        return verseNoSB.toString()
    }

    companion object {
        private val log = Logger("VerseHandler")
    }

}
