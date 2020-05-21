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
package net.bible.service.format.osistohtml.tei

import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.taghandler.HiHandler
import org.xml.sax.Attributes

/** Handle orth tag very similarly to hi tag
 * <orth>?????????</orth>
 * <orth rend="bold" type="trans">aneuthetos</orth>
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OrthHandler(parameters: OsisToHtmlParameters, writer: HtmlTextWriter) : HiHandler(parameters, writer) {
    override val tagName: String = TEIUtil.TEI_ELEMENT_ORTH

    override fun start(attrs: Attributes) {
        val rend = attrs.getValue(TEIUtil.TEI_ATTR_REND)
        start(rend, DEFAULT)
    }

    companion object {
        private const val DEFAULT = "bold"
    }
}
