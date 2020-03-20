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


import android.os.Build;
import android.text.Html;
import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.taghandler.TagHandlerHelper;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

import java.util.Stack;
/**
 * Convert OSIS input into Canonical text (used when creating search index)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class OsisToCanonicalTextSaxHandler extends OsisSaxHandler {
    
    @SuppressWarnings("unused")
	private int currentVerseNo;

    protected Stack<CONTENT_STATE> writeContentStack = new Stack<>();
	protected enum CONTENT_STATE {WRITE, IGNORE}

	// Avoid space at the start and, extra space between words
	private boolean spaceJustWritten = true;
    
	private static final Logger log = new Logger("OsisToCanonicalTextSaxHandler");
    
    public OsisToCanonicalTextSaxHandler() {
        super();
    }

    @Override
    public void startDocument () {
    	reset();
    	// default mode is to write
    	writeContentStack.push(CONTENT_STATE.WRITE);
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    @Override
    public void endDocument() {
    	// pop initial value
    	writeContentStack.pop();
    	
    	// assert
    	if (!writeContentStack.isEmpty()) {
    		log.warn("OsisToCanonicalTextSaxHandler context stack should now be empty");
    	}
    }

    /*
     * Called when the starting of the Element is reached. For Example if we have Tag
     * called <Title> ... </Title>, then this method is called when <Title> tag is
     * Encountered while parsing the Current XML File. The AttributeList Parameter has
     * the list of all Attributes declared for the Current Element in the XML File.
    */
    @Override
    public void startElement(String namespaceURI,
            String sName, // simple name
            String qName, // qualified name
            Attributes attrs)
    {
		String name = getName(sName, qName); // element name

		debug(name, attrs, true);

		// if encountering either a verse tag or if the current tag is marked as being canonical then turn on writing
		if (isAttrValue(attrs, "canonical", "true")) {
			writeContentStack.push(CONTENT_STATE.WRITE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			if (attrs!=null) {
				currentVerseNo = TagHandlerHelper.osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
			}
			writeContentStack.push(CONTENT_STATE.WRITE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			// text content of top level references should be output but in notes it should not
			writeContentStack.push(writeContentStack.peek());
		} else if (	name.equals(OSISUtil.OSIS_ELEMENT_L) ||
					name.equals(OSISUtil.OSIS_ELEMENT_LB) ||
					name.equals(OSISUtil.OSIS_ELEMENT_P) ) {
			// these occur in Psalms to separate different paragraphs.  
			// A space is needed for TTS not to be confused by punctuation with a missing space like 'toward us,and the'
			write(" ");
			//if writing then continue.  Also if ignoring then continue
			writeContentStack.push(writeContentStack.peek());
		} else {
			// unknown tags rely on parent tag to determine if content is canonical e.g. the italic tag in the middle of canonical text
			writeContentStack.push(writeContentStack.peek());
		}
	}
    
    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    @Override
    public void endElement(String namespaceURI,
            String sName, // simple name
            String qName  // qualified name
            )
    {
		String name = getName(sName, qName);
		debug(name, null, false);
		if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			// A space is needed to separate one verse from the next, otherwise the 2 verses butt up against each other
			// which looks bad and confuses TTS
			write(" ");
		}
		
		// now this tag has ended pop the write/ignore state for the parent tag
		writeContentStack.pop();
	}
    
    /*
     * Handle characters encountered in tags
    */
    @Override
    public void characters (char buf[], int offset, int len) {
        if (CONTENT_STATE.WRITE.equals(writeContentStack.peek())) {
        	String s = new String(buf, offset, len);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				if(s.length() > 0 && s.charAt(0) == ' ') {
					// fromHtml strips leading whitespaces, which is not desirable
					write(" ");
				}
				s = Html.fromHtml(s, 0).toString();
			}
			write(s);
        }
    }

	@Override
	protected void write(String s) {
		// reduce amount of whitespace becasue a lot of space was occurring between verses in ESVS and several other books
		if (!StringUtils.isWhitespace(s)) {
			super.write(s);
			spaceJustWritten = Character.isWhitespace(s.charAt(s.length() - 1));
		} else if (!spaceJustWritten) {
			super.write(" ");
			spaceJustWritten = true;
		}
	}

	protected void writeContent(boolean writeContent) {
    	if (writeContent) {
    		writeContentStack.push(CONTENT_STATE.WRITE);    		
    	} else {
    		writeContentStack.push(CONTENT_STATE.IGNORE);
    	}
    }
}

