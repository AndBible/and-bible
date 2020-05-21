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
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.PassageInfo
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

/** Line break
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class LbHandler(
    private val parameters: OsisToHtmlParameters,
    private val passageInfo: PassageInfo,
    private val writer: HtmlTextWriter) : OsisTagHandler
{
    override val tagName: String get() {
        return OSISUtil.OSIS_ELEMENT_LB
    }

    override fun start(attrs: Attributes) {
        if (passageInfo.isAnyTextWritten) {
            writer.write(Constants.HTML.BR)
        }
    }

    override fun end() {}

    companion object {
        private val log = Logger("LHandler")
    }

}
