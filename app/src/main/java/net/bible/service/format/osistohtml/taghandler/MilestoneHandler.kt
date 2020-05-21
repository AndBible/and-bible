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
import net.bible.service.format.osistohtml.OSISUtil2
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.PassageInfo
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

/**
 * Continuation quotation marks
 * ----------------------------
 * The <milestone type="cQuote"></milestone> can be used to indicate the presence of a continued quote.
 * If the marker attribute is present, it will use that otherwise it will use a straight double quote, ".
 * Since there is no level attribute on the milestone element, it is best to specify the marker attribute.
 * http://www.crosswire.org/wiki/OSIS_Bibles#Continuation_quotation_marks
 *
 * Example from ESV
 * diatheke -b ESV -f OSIS -k Jn 3:16
 * John 3:16:
 * <q marker=""><milestone marker="“" type="cQuote"></milestone>For God ... eternal life.</q><milestone type="line"></milestone>
 *
 *
 * New Line
 * --------
 * Can signify a new line is required
 *
 * Example from KJV Gen 1:6
 * <verse osisID='Gen.1.6'><milestone marker="¶" type="x-p"></milestone><w lemma="strong:H0430">And God</w>
 *
 *
 * Example from NETtext Mt 4:14
 *
 * @author Martin Denham [mjdenham at gmail dot com]
</verse> */
class MilestoneHandler(private val parameters: OsisToHtmlParameters, private val passageInfo: PassageInfo, private val verseInfo: VerseInfo, private val writer: HtmlTextWriter) : OsisTagHandler {
    override val tagName: String get() {
        return OSISUtil2.OSIS_ELEMENT_MILESTONE
    }

    override fun start(attrs: Attributes) {
        val type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE)
        if (StringUtils.isNotEmpty(type)) {
            when (type) {
                "x-p", "line" -> if (passageInfo.isAnyTextWritten) {
                    // if no verse text has yet been written then place the BR before the verse number
                    writer.writeOptionallyBeforeVerse(Constants.HTML.BR, verseInfo)
                }
                "cQuote" -> {
                    val marker = TagHandlerHelper.getAttribute(OSISUtil2.OSIS_ATTR_MARKER, attrs, HTML_QUOTE_ENTITY)
                    writer.write(marker)
                }
                else -> log.debug("Verse " + verseInfo.currentVerseNo + " unsupported milestone type:" + type)
            }
        }
    }

    override fun end() {}

    companion object {
        private const val HTML_QUOTE_ENTITY = "&quot;"
        private val log = Logger("OsisToHtmlSaxHandler")
    }

}
