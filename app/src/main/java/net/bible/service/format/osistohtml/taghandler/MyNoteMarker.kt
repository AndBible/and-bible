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

import net.bible.service.common.Logger
import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.crosswire.jsword.passage.KeyUtil
import org.xml.sax.Attributes
import java.util.*

/** Display an img if the current verse has MyNote
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNoteMarker(private val parameters: OsisToHtmlParameters,
                   private val verseInfo: VerseInfo,
                   private val writer: HtmlTextWriter) : OsisTagHandler {
    private val myNoteVerses: MutableSet<Int>? = HashSet()
    override fun getTagName() = ""

    /** just after verse start tag
     */
    override fun start(attr: Attributes) {
        if (myNoteVerses != null && parameters.isShowMyNotes) {
            if (myNoteVerses.contains(verseInfo.currentVerseNo)) {
                writer.write("<img src='file:///android_asset/images/pencil16x16.png' class='myNoteImg'/>")
            }
        }
    }

    override fun end() {}

    companion object {
        private val log = Logger("MyNoteMarker")
    }

    init {
        // create hashmap of verses to optimise verse note lookup
        if (parameters.versesWithNotes != null) {
            for (key in parameters.versesWithNotes) {
                val verse = KeyUtil.getVerse(key)
                myNoteVerses.add(verse.verse)
            }
        }
    }
}
