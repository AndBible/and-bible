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

package net.bible.service.format.osistohtml.tei;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.taghandler.NoteHandler;
import net.bible.service.format.osistohtml.taghandler.ReferenceHandler;

import org.xml.sax.Attributes;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class RefHandler extends ReferenceHandler {

    public RefHandler(OsisToHtmlParameters osisToHtmlParameters, NoteHandler noteHandler, HtmlTextWriter theWriter) {
        super(osisToHtmlParameters, noteHandler, theWriter);
    }
    
    @Override
	public String getTagName() {
		return TEIUtil.TEI_ELEMENT_REF;
	}

    @Override
	public void start(Attributes attrs) {
		String target = attrs.getValue(TEIUtil.TEI_ATTR_TARGET);
		start(target);
	}
}
