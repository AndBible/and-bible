/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: Tag.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/**
 * OSIS Tag interface - there should be one implementation of this class for
 * each OSIS tag.
 * 
 */
public interface TagHandler {
    /**
     * What element does this class represent. For example the Tag that
     * represents the &gtfont ...> element would return the string "font".
     */
    String getTagName();

    /** create any data used by open/closeTag
     */
    TagHandlerData createData(Attributes attrs, CommonHandlerData commonHandlerData);
    
    /**
     * Make changes to the specified OSIS element given the data passed in
     * the tagHandlerData.
     */
    void start(TagHandlerData tagHandlerData, CommonHandlerData commonHandlerData);

    /**
     * This is called after the contents of the tag have been processed
     */
    void end(TagHandlerData tagHandlerData, CommonHandlerData commonHandlerData);
}
