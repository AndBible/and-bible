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
 * The main content of a list is encoded using the item element.
 * See ListHandler for full description
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ListItemHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public ListItemHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_ITEM;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<li>");
	}

	@Override
	public void end() {
		writer.write("</li>");
	}
}
