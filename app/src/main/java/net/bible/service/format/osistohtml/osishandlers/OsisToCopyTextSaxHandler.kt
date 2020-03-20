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

package net.bible.service.format.osistohtml.osishandlers


import net.bible.service.common.CommonUtils
import net.bible.service.format.osistohtml.taghandler.TagHandlerHelper

import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

/**
 * Convert OSIS input into text (for copying and sharing verses)
 *
 * @author Timmy Braun [tim.bze at gmail dot com] (4/24/2019)
 */
class OsisToCopyTextSaxHandler(val showVerseNumbers: Boolean) : OsisToCanonicalTextSaxHandler() {

    private var currentChapterNumber: Int = 0
    private var currentVerseNumber: Int = 0
    private var writeChapter: String = ""

    override fun startElement(namespaceURI: String?,
                              sName: String?, // simple name
                              qName: String, // qualified name
                              attrs: Attributes?) {

        when (getName(sName, qName)) {
            OSISUtil.OSIS_ELEMENT_CHAPTER -> {
                if (attrs != null && showVerseNumbers) {
                    val oldChapterNumber = currentChapterNumber
                    currentChapterNumber = TagHandlerHelper.osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID))
                    if (oldChapterNumber > 0 && currentChapterNumber > oldChapterNumber) {
                        writeChapter = "$currentChapterNumber:"
                    }
                }
            }
            OSISUtil.OSIS_ELEMENT_VERSE -> {
                if (attrs != null && showVerseNumbers) {
                    val osisID = attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID)
                    currentVerseNumber = TagHandlerHelper.osisIdToVerseNum(osisID)

                    // if block necessary for JUnit tests and quite possibly will be needed in some cases in production
                    if (currentChapterNumber == 0) {
                        val parts = osisID.split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                        if (parts.size > 2) {
                            val chapter = parts[parts.size - 2]
                            currentChapterNumber = Integer.valueOf(chapter)
                        }
                    }

                    write("$writeChapter$currentVerseNumber. ")
                    writeContentStack.push(writeContentStack.peek())
                    writeChapter = ""
                }
            }
        }

        super.startElement(namespaceURI, sName, qName, attrs)
    }
}

