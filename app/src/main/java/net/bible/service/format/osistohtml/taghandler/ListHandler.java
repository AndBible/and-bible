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
 * A list element can be used for outlines that sometimes preceed or follow a biblical passage, such as:
 * <list>
 *   <head>Outline</head>
 *   <item><label>I.</label> The Feasts of Xerxes (1:1-2.18)</item>
 *   <item>
 *     <list>
 *       <item><label>A.</label> Vashti Deposed (ch. 1)</item>
 *       <item><label>B.</label> Esther Made Queen (2:1-18)</item>
 *     </list>
 *   </item>
 * </list>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ListHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public ListHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_LIST;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<ul>");
	}

	@Override
	public void end() {
		writer.write("</ul>");
	}
}
