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
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes
import java.util.*

/** This can either signify a quote or Red Letter
 * Example
 * ESV section heading 	<title subType="x-preverse" type="section">
 * ESV canonical heading<title canonical="true" subType="x-preverse" type="section">To the choirmaster. Of David,
 * WEB when formatted with JSword seems to have type="x-gen"
 *
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
</title></title> */
class TitleHandler(private val parameters: OsisToHtmlParameters, private val verseInfo: VerseInfo, private val writer: HtmlTextWriter) : OsisTagHandler {
    private var isShowTitle = false
    private var isMoveBeforeVerse = false
    override val tagName: String
        get() = OSISUtil.OSIS_ELEMENT_TITLE

    override fun start(attrs: Attributes) {
        //JSword adds the chapter no at the top but hide this because the chapter is in the And Bible header
        val addedByJSword = attrs.length == 1 && OSISUtil.GENERATED_CONTENT == attrs.getValue(OSISUtil.OSIS_ATTR_TYPE)
        // otherwise show if user wants Titles or the title is canonical
        isShowTitle = !addedByJSword &&
            (parameters.isShowTitles ||
                "true".equals(attrs.getValue(OSISUtil.OSIS_ATTR_CANONICAL), ignoreCase = true))
        if (isShowTitle) {
            // ESV has subType butNETtext has lower case subtype so concatenate both and search with contains()
            val subtype = attrs.getValue(OSISUtil.OSIS_ATTR_SUBTYPE) + attrs.getValue(OSISUtil.OSIS_ATTR_SUBTYPE.toLowerCase(Locale.ENGLISH))
            isMoveBeforeVerse = StringUtils.containsIgnoreCase(subtype, PREVERSE) || !verseInfo.isTextSinceVerse && verseInfo.currentVerseNo > 0
            if (isMoveBeforeVerse) {
                // section Titles normally come before a verse, so overwrite the, already written verse, which is rewritten on writer.finishedInserting
                writer.beginInsertAt(verseInfo.positionToInsertBeforeVerse)
            }

            // get title type from level
            val titleClass = "heading" + TagHandlerHelper.getAttribute(OSISUtil.OSIS_ATTR_LEVEL, attrs, "1")
            writer.write("<h1 class='$titleClass'>")
        } else {
            writer.setDontWrite(true)
        }
    }

    override fun end() {
        if (isShowTitle) {
            writer.write("</h1>")
            if (isMoveBeforeVerse) {
                // move positionToInsertBeforeVerse forward to after this title otherwise any subtitle will be above the title
                verseInfo.positionToInsertBeforeVerse = writer.position
                writer.finishInserting()
            }
        } else {
            writer.setDontWrite(false)
        }
    }

    companion object {
        private const val PREVERSE = "preverse" // the full string is 'x-preverse' but we just check for contains for extra tolerance
        private val log = Logger("TitleHandler")
    }

}
