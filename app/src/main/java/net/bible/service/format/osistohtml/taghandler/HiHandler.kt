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

/** Handle hi element e.g. <hi type="italic">the child with his mother Mary</hi>
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class HiHandler(parameters: OsisToHtmlParameters?, private val writer: HtmlTextWriter) : OsisTagHandler {
    override val tagName: String get() {
        return OSISUtil.OSIS_ELEMENT_HI
    }

    override fun start(attrs: Attributes) {
        val type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE)
        start(type, DEFAULT)
    }

    /**
     * Used by TEI handlers
     */
    protected fun start(style: String?, defaultStyle: String?) {
        // if not a standard style or begins with 'x-' then use default style
        var style = style
        if (style == null ||
            !(HI_TYPE_LIST.contains(style) || style.startsWith("x-"))) {
            style = defaultStyle
        }

        // add any styles that are relevant - the tag name and the style attribute
        val cssClasses = "$tagName hi_$style"

        // start span with CSS class of 'hi_*' e.g. hi_bold
        writer.write("<span class=\'$cssClasses\'>")
    }

    override fun end() {
        writer.write("</span>")
    }

    companion object {
        // possible values of type attribute
        private val HI_TYPE_LIST = listOf(OSISUtil.HI_ACROSTIC, OSISUtil.HI_BOLD,
            OSISUtil.HI_EMPHASIS, OSISUtil.HI_ILLUMINATED, OSISUtil.HI_ITALIC, OSISUtil.HI_LINETHROUGH,
            OSISUtil.HI_NORMAL, OSISUtil.HI_SMALL_CAPS, OSISUtil.HI_SUB, OSISUtil.HI_SUPER, OSISUtil.HI_UNDERLINE)
        private const val DEFAULT = "bold"
    }

}
