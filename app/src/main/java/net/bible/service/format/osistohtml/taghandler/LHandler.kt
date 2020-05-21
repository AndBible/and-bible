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
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes
import java.util.*

/** This can either signify a quote or Red Letter
 * Example from ESV Prov 19:1
 * <l sID="x9938"></l>...<l eID="x9938" type="x-br"></l><l sID="x9939" type="x-indent"></l>..<l eID="x9939" type="x-br"></l>
 *
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 *
 * http://www.crosswire.org/wiki/List_of_eXtensions_to_OSIS_used_in_SWORD
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class LHandler(private val parameters: OsisToHtmlParameters, private val writer: HtmlTextWriter) : OsisTagHandler {
    private enum class LType {
        INDENT, BR, END_BR, IGNORE
    }

    private val stack = Stack<LType>()

    /* (non-Javadoc)
	 * @see net.bible.service.format.osistohtml.Handler#getTagName()
	 */
    override val tagName: String get() {
        return OSISUtil.OSIS_ELEMENT_L
    }

    /* (non-Javadoc)
	 * @see net.bible.service.format.osistohtml.Handler#start(org.xml.sax.Attributes)
	 */
    override fun start(attrs: Attributes) {
        // Refer to Gen 3:14 in ESV for example use of type=x-indent
        val type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE)
        val level = TagHandlerHelper.getAttribute(OSISUtil.OSIS_ATTR_LEVEL, attrs, 1)
        // make numIndents default to zero
        var numIndents = Math.max(0, level - 1)
        val ltype: LType
        if (TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_EID, attrs)) {
            // e.g. Isaiah 40:12
            writer.write(Constants.HTML.BR)
            ltype = LType.BR
        } else if (StringUtils.isNotEmpty(type)) {
            if (type.contains("indent")) {
                // this tag is specifically for indenting so ensure there is an indent
                numIndents = numIndents + 1
                writer.write(StringUtils.repeat(indent_html, numIndents))
                ltype = LType.INDENT
            } else if (type.contains("br")) {
                writer.write(Constants.HTML.BR)
                ltype = LType.BR
            } else {
                ltype = LType.IGNORE
                log.debug("Unknown <l> tag type:$type")
            }
        } else if (TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_SID, attrs)) {
            writer.write(StringUtils.repeat(indent_html, numIndents))
            ltype = LType.IGNORE
        } else {
            //simple <l>
            writer.write(StringUtils.repeat(indent_html, numIndents))
            ltype = LType.END_BR
        }
        stack.push(ltype)
    }

    /* (non-Javadoc)
	 * @see net.bible.service.format.osistohtml.Handler#end()
	 */
    override fun end() {
        val type = stack.pop()
        if (LType.END_BR == type) {
            writer.write(Constants.HTML.BR)
        }
    }

    companion object {
        private var indent_html = Constants.HTML.NBSP + Constants.HTML.NBSP
        private val log = Logger("LHandler")
    }

    init {
        indent_html = StringUtils.repeat(Constants.HTML.NBSP, parameters.indentDepth)
    }
}
