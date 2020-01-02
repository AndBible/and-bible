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

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/**
 * A new row within a table.
 * See TableHandler for full description and example.
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TableRowHandler implements OsisTagHandler{

	private HtmlTextWriter writer;
	
	public TableRowHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_ROW;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<tr>");
	}

	@Override
	public void end() {
		writer.write("</tr>");
	}
}
