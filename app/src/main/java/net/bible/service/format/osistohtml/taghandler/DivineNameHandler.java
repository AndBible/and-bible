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

package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OSISUtil2;

import org.xml.sax.Attributes;

/**
 * The <divineName> tag is reserved for representations of the tetragrammaton יהוה (YHWH). 
 * These occur in the Old Testament as Lord, God and Yah. Not every instance of Lord or God is a translation of this.
 * E.g. <divineName type="yhwh">LORD</divineName>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DivineNameHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public DivineNameHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil2.OSIS_ELEMENT_DIVINENAME;
    }

	@Override
	public void start(Attributes attrs) {
		// start span with CSS class
		writer.write("<span class='divineName'>");	}

	@Override
	public void end() {
		writer.write("</span>");
	}
}
