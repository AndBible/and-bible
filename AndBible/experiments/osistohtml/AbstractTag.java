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
 * ID: $Id: AbstractTag.java 1966 2009-10-30 01:15:14Z dmsmith $
 */
package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

/**
 * The AbstractTag ignores the tag.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public abstract class AbstractTag implements Tag {
	
	HtmlTextWriter writer;
	CommonTagState commonTagState;
	
	@Override
	public void start(Tag parentTag, Attributes attrs) {
		// ignore tag by default
		
	}

	@Override
	public void end(String content) {
		// TODO Auto-generated method stub
		
	}

	public HtmlTextWriter getWriter() {
		return writer;
	}
	public void setWriter(HtmlTextWriter writer) {
		this.writer = writer;
	}

	public CommonTagState getCommonTagState() {
		return commonTagState;
	}
	public void setCommonTagState(CommonTagState commonTagState) {
		this.commonTagState = commonTagState;
	}
}
