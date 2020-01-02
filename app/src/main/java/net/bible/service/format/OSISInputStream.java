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

package net.bible.service.format;

import net.bible.service.common.Logger;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

/** Read through the raw OSIS input from a bible, add verse tags if required, remove any extra div tags,
 * and pipe back as in InputStream ready to be fed to the SAXParser for html formatting.
 * This is more efficient than using JDom to create a DOM and then streaming the DOM into a SAX parser  
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class OSISInputStream extends InputStream {
	// requested passage
	private Book book;
	@SuppressWarnings("unused")
	private Key key;
	
	// iterator
	private boolean isFirstVerse = true;
	private Iterator<Key> keyIterator;
	private boolean isClosingTagWritten = false;
	
	// allow avoidance of repeated text due to merged verses
	private String previousVerseRawText = "";
	
	// cache
	private byte[] verseBuffer;
	private int length = 0;
	private int next = 0;
	
	//todo get the proper ENTITY refs working rather than my simple 2 fixed entities
//	private static final String DOC_START = "<!ENTITY % HTMLlat1 PUBLIC \"-//W3C//ENTITIES Latin 1 for XHTML//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml-lat1.ent\">\n%HTMLlat1;\n<div>";
	private static final String DOC_START =	"<!DOCTYPE div [<!ENTITY nbsp \"&#160;\"><!ENTITY copy \"&#169;\">]><div>";
	private static final String DOC_END =	"</div>";

	private OSISVerseTidy osisVerseTidy;
	
	private static String TAG = "OSISInputStream";

	private static Logger log = new Logger(TAG);
	
	/** Constructor to create an input stream from raw OSIS input
	 * @param book
	 * @param key
	 */
	public OSISInputStream(Book book, Key key) {
		this.book = book;
		this.key = key;
		osisVerseTidy  = new OSISVerseTidy(book);
		keyIterator = key.iterator();
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
	@Override
	public int read() throws IOException {
		if (available()>0) {
			return verseBuffer[next++];
		} 
		return -1;
	}


	/* (non-Javadoc)
	 * @see java.io.InputStream#available()
	 */
	@Override
	public int available() throws IOException {
		if (next>=length) {
			loadNextVerse();
		}
		return length-next;
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#read(byte[], int, int)
	 */
	@Override
	public int read(byte[] b, int bOffset, int lenToRead) throws IOException {
		if (b == null) {
			throw new NullPointerException();
		} else if ((bOffset < 0) || (bOffset > b.length) || (lenToRead < 0)
				|| ((bOffset + lenToRead) > b.length) || ((bOffset + lenToRead) < 0)) {
			throw new IndexOutOfBoundsException();
		} else if (lenToRead == 0) {
			return 0;
		}
		
		int available = available();
		// have we reached the end of the chapter
		if (available==0) {
			return -1;
		}
		
		int lenActuallyCopied = Math.min(available, lenToRead);
		
		System.arraycopy(verseBuffer, next, b, bOffset, lenActuallyCopied);
		next += lenActuallyCopied;
		return lenActuallyCopied;
	}

	/** load the next verse, or opening or closing <div> into the verse buffer
	 * 
	 * @throws UnsupportedEncodingException
	 */
	private void loadNextVerse() throws UnsupportedEncodingException {
		try {
			if (isFirstVerse) {
				putInVerseBuffer(DOC_START);
				isFirstVerse = false;
				return;
			}
		
			boolean isNextVerseLoaded = false;
			while (keyIterator.hasNext() && !isNextVerseLoaded) {
				Key currentVerse = keyIterator.next();
				//get the actual verse text and tidy it up,
				String rawText;

				try {
					rawText = book.getRawText(currentVerse);
				} catch (BookException e) {
					if(e.getMessage().startsWith("Unable to obtain raw content")) {
						rawText = "";
					}
					else {
						throw e;
					}
				}
				
				// do not output empty verses (commonly verse 0 is empty)
				if (!StringUtils.isWhitespace(rawText)) {
					
					// merged verses can cause duplicates so if dup then skip immediately to next verse
					if (!previousVerseRawText.equals(rawText)) {
						String tidyText = osisVerseTidy.tidy(currentVerse, rawText);
						putInVerseBuffer(tidyText);
						previousVerseRawText = rawText;
						isNextVerseLoaded = true;
						return;
					} else {
						log.debug("Duplicate verse:"+currentVerse);
					}
					
				} else {
					log.debug("Empty or missing verse:"+currentVerse);
				}
			}
			
			if (!isClosingTagWritten) {
				putInVerseBuffer(DOC_END);
				isClosingTagWritten = true;
			}
		} catch (UnsupportedEncodingException usc) {
			usc.printStackTrace();
		} catch (BookException be) {
			be.printStackTrace();
		}
	}

    /** put the text into the verse buffer ready to be fed into the stream
     * 
     * @param text
     * @throws UnsupportedEncodingException
     */
	private void putInVerseBuffer(String text) throws UnsupportedEncodingException {
//		log.debug(text);
		verseBuffer = text.getBytes("UTF-8");
		length = verseBuffer.length;
		next = 0;
	}
}
