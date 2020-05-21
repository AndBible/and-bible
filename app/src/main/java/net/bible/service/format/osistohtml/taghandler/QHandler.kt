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

import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes
import java.util.*

/** This can either signify a quote or Red Letter.  Red letter is not implemented in milestone form because it maps onto opening and closing tags around text.
 * Example from ESV
 * But he answered them, <q marker="" who="Jesus"><q level="1" marker="�" sID="40024002.1"></q>You see all these
 * Example from KJV
 * said ... unto them, <q who="Jesus">...See ye
 *
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
</q></q> */
class QHandler(private val parameters: OsisToHtmlParameters, private val writer: HtmlTextWriter) : OsisTagHandler {

    // quotes can be embedded so maintain a stack of info about each quote to be used when closing quote
    private val stack = Stack<QuoteInfo>()

    internal enum class QType {
        quote, redLetter
    }

    override val tagName: String
        get() = OSISUtil.OSIS_ELEMENT_Q

    override fun start(attrs: Attributes) {
        val quoteInfo = QuoteInfo()
        val who = attrs.getValue(OSISUtil.ATTRIBUTE_Q_WHO)
        val isWho = who != null
        quoteInfo.isMilestone = TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_SID, attrs) || TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_EID, attrs)

        // Jesus -> no default quote
        quoteInfo.marker = TagHandlerHelper.getAttribute(MARKER, attrs, if (isWho) "" else HTML_QUOTE_ENTITY)
        quoteInfo.isRedLetter = parameters.isRedLetter && "Jesus" == who

        // apply the above logic
        writer.write(quoteInfo.marker)
        if (quoteInfo.isRedLetter) {
            writer.write("<span class='redLetter'>")
        }

        // and save the info for the closing tag
        stack.push(quoteInfo)
    }

    override fun end() {
        val quoteInfo = stack.pop()

        // Jesus words
        if (quoteInfo.isRedLetter) {
            writer.write("</span>")
        }

        // milestone opening and closing tags are doubled up so ensure not double quotes
        if (!quoteInfo.isMilestone) {
            writer.write(quoteInfo.marker)
        }
    }

    private class QuoteInfo {
        var marker: String? = HTML_QUOTE_ENTITY
        var isMilestone = false
        var isRedLetter = false
    }

    companion object {
        private const val MARKER = "marker"
        private const val HTML_QUOTE_ENTITY = "&quot;"
    }

}
