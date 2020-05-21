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
import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.xml.sax.Attributes

/**
 * Write the chapter number at the beginning of a Bible chapter
 * The chapter id is also useful for going to the start of a chapter (verse 1)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChapterDivider(
    private val parameters: OsisToHtmlParameters,
    private val verseInfo: VerseInfo,
    private val writer: HtmlTextWriter) : OsisTagHandler
{
    override val tagName: String = "NotLinkedToOsisChapterElement"

    override fun start(attrs: Attributes) {
        doStart()
    }

    fun doStart() {
        val chapter = parameters.chapter
        if (parameters.isShowChapterDivider && chapter != null) {
            if (chapter > 1) {
                if (parameters.isShowVerseNumbers) {
                    writer.write("<div class='chapterNo'>&#8212; $chapter &#8212;</div>")
                }
            }
            // used to jump to the top of a chapter, but still allow up scroll
            writer.write("<span class='position-marker' id='" + chapter + "'>" + Constants.HTML.EMPTY_SPACE + "</span>")
            verseInfo.positionToInsertBeforeVerse = writer.position
        }
    }

    override fun end() {}

}
