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

package net.bible.service.format.osistohtml.osishandlers;


import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
/**
 * Convert OSIS input into Canonical text (used when creating search index)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class OsisSaxHandler extends DefaultHandler {
    
    // debugging
    private boolean isDebugMode = false;

    private HtmlTextWriter writer;
    
    @SuppressWarnings("unused")
	private static final Logger log = new Logger("OsisSaxHandler");
    
    public OsisSaxHandler() {
        writer = new HtmlTextWriter();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    /* @Override */
    public String toString() {
        return writer.getHtml();
    }

    protected String getName(String eName, String qName) {
        if (eName!=null && eName.length()>0) {
            return eName;
        } else {
            return qName; // not namespace-aware
        }
    }
    
	protected void write(String s) {
		writer.write(s);
	}

    /** check the value of the specified attribute and return true if same as checkvalue
     * 
     * @param attrs
     * @param attrName
     * @param checkValue
     * @return
     */
    protected boolean isAttrValue(Attributes attrs, String attrName, String checkValue) {
    	if (attrs==null) {
    		return false;
    	}
    	String value = attrs.getValue(attrName);
    	return checkValue.equals(value);
    }
    
    protected void debug(String name, Attributes attrs, boolean isStartTag) {
	    if (isDebugMode) {
	        write("*"+name);
	        if (attrs != null) {
	          for (int i = 0; i < attrs.getLength(); i++) {
	            String aName = attrs.getLocalName(i); // Attr name
	            if ("".equals(aName)) aName = attrs.getQName(i);
	            write(" ");
	            write(aName+"=\""+attrs.getValue(i)+"\"");
	          }
	        }
	        write("*\n");
	    }
    }    

	public void setDebugMode(boolean isDebugMode) {
		this.isDebugMode = isDebugMode;
	}
	protected void reset() {
		writer.reset();
	}

	public HtmlTextWriter getWriter() {
		return writer;
	}
}

