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

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TEIUtil {
	
	// E.g. <ref target="StrongsHebrew:00411">H411</ref> taken from StrongsHebrew:00428
    public static final String TEI_ELEMENT_REF = "ref";
    public static final String TEI_ATTR_TARGET = "target";

    public static final String TEI_ELEMENT_ORTH = "orth";
    public static final String TEI_ELEMENT_PRON = "pron";
    // the way tag contents are rendered e.g. 'bold'. 'italic'
    public static final String TEI_ATTR_REND = "rend";
}
