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

import java.util.Stack;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** This can either signify a quote or Red Letter.  Red letter is not implemented in milestone form because it maps onto opening and closing tags around text.
 * Example from ESV 
 * 		But he answered them, <q marker="" who="Jesus"><q level="1" marker="�" sID="40024002.1"/>You see all these
 * Example from KJV
 * 		said ... unto them, <q who="Jesus">...See ye
 * 
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class QHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	// quotes can be embedded so maintain a stack of info about each quote to be used when closing quote
	private Stack<QuoteInfo> stack = new Stack<QuoteInfo>();
	
	enum QType {quote, redLetter};
	private static final String MARKER = "marker";
	private static final String HTML_QUOTE_ENTITY = "&quot;";

	public QHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_Q;
    }

	@Override
	public void start(Attributes attrs) {
		QuoteInfo quoteInfo = new QuoteInfo();

		String who = attrs.getValue(OSISUtil.ATTRIBUTE_Q_WHO);
		boolean isWho = who!=null;

		quoteInfo.isMilestone = TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_SID, attrs) || TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_EID, attrs); 

		// Jesus -> no default quote
		quoteInfo.marker = TagHandlerHelper.getAttribute(MARKER, attrs, isWho ? "" : HTML_QUOTE_ENTITY);

		quoteInfo.isRedLetter = parameters.isRedLetter() && "Jesus".equals(who);

		// apply the above logic
		writer.write(quoteInfo.marker);
		if (quoteInfo.isRedLetter) {
			writer.write("<span class='redLetter'>");
		}
		
		// and save the info for the closing tag
		stack.push(quoteInfo);
	}

	@Override
	public void end() {
		QuoteInfo quoteInfo = stack.pop();
		
		// Jesus words
		if (quoteInfo.isRedLetter) {
			writer.write("</span>");
		}
		
		// milestone opening and closing tags are doubled up so ensure not double quotes
		if (!quoteInfo.isMilestone) {
			writer.write(quoteInfo.marker);
		}
	}
	
	private static class QuoteInfo {
		private String marker = HTML_QUOTE_ENTITY;
		private boolean isMilestone;
		private boolean isRedLetter;
	}
}
