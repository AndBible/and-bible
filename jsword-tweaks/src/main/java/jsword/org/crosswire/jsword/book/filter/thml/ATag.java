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
 * ID: $Id: ATag.java 2050 2010-12-09 15:31:45Z dmsmith $
 */
package org.crosswire.jsword.book.filter.thml;

import org.crosswire.jsword.book.OSISUtil;
import org.jdom.Element;
import org.xml.sax.Attributes;

/**
 * THML Tag to process the a (Reference) element.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class ATag extends AbstractTag {
    /*
     * (non-Javadoc)
     * 
     * @see org.crosswire.jsword.book.filter.thml.Tag#getTagName()
     */
    public String getTagName() {
        return "a";
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.filter.thml.Tag#processTag(org.jdom.Element,
     * org.xml.sax.Attributes)
     */
    @Override
    public Element processTag(Element ele, Attributes attrs) {
        Element reference = OSISUtil.factory().createReference();

        String href = attrs.getValue("href");
        if (href!=null && !href.isEmpty()) {
            reference.setAttribute(OSISUtil.OSIS_ATTR_REF, href);
        }
        
        if (ele != null) {
            ele.addContent(reference);
        }

        return reference;
    }
}
