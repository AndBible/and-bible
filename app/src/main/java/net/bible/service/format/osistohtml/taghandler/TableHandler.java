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
 * OSIS provides only very rudimentary tables: a table consists of rows, which in turn consist of cells.
 * Formatting and layout is not part of the table markup; it can either be done automatically, as in HTML
 * browsers, or by inserting some signal to the layout engine, such as type attributes or processing instructions.
 * Note that a table can be nested inside another table. Simply start a new table element inside a cell element.
 *  
 * <table>
 *   <row><cell role="label">Tribe </cell><cell role="label">Administrator</cell></row>
 *   <row><cell>Reuben </cell><cell>Eliezer son of Zichri</cell></row>
 *   <row><cell>Simeon </cell><cell>Shephatiah son of Maacah</cell></row>
 * </table>
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TableHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	public TableHandler(HtmlTextWriter writer) {
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_TABLE;
    }

	@Override
	public void start(Attributes attrs) {
		writer.write("<table>");
	}

	@Override
	public void end() {
		writer.write("</table>");
	}
}
