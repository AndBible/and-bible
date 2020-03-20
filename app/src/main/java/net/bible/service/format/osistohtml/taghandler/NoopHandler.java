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
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.xml.sax.Attributes;

/** The lg or "line group" element is used to contain any group of poetic lines.  Poetic lines are handled at the line level by And Bible, not line group 
 * so this class does nothing.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class NoopHandler implements OsisTagHandler {

	public NoopHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
	}

	@Override
	public String getTagName() {
        return "";
    }

	@Override
	public void start(Attributes attrs) {
	}

	@Override
	public void end() {
	}
}
