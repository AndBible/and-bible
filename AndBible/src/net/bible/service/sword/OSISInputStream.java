package net.bible.service.sword;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;


import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;

/** Read through the raw OSIS input from a bible, add verse tags if required, remove any extra div tags,
 * and pipe back as in InputStream ready to be fed to the SAXParser for html formatting.
 * This is more efficient than using JDom to create a DOM and then streaming teh DOM into a SAX parser  
 * 
 * @author denha1m
 */
public class OSISInputStream extends InputStream {
	// requested passage
	private Book book;
	private Key key;
	
	// iterator
	private boolean isFirstVerse = true;
	private Iterator<Verse> verseIterator;
	private boolean isClosingTagWritten = false;
	
	// cache
	private byte[] verseBuffer;
	private int length = 0;
	private int next = 0;
	
	private static String TAG = "OSISInputStream";
	
	private OSISVerseTidy osisVerseTidy;
	
	private static Logger log = new Logger(TAG);
	
	
	/** Constructor to create an input stream from raw OSIS input
	 * @param book
	 * @param key
	 */
	public OSISInputStream(Book book, Key key) {
		this.book = book;
		this.key = key;
		osisVerseTidy  = new OSISVerseTidy(book);
		verseIterator = key.iterator();
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
	//todo get the proper ENTITY refs working rather than my simple 2 fixed entities
//	private static final String DOC_START = "<!ENTITY % HTMLlat1 PUBLIC \"-//W3C//ENTITIES Latin 1 for XHTML//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml-lat1.ent\">\n%HTMLlat1;\n<div>";
	private static final String DOC_START =	"<!DOCTYPE div [<!ENTITY nbsp \"&#160;\"><!ENTITY copy \"&#169;\">]><div>";
	private void loadNextVerse() throws UnsupportedEncodingException {
		try {
			if (isFirstVerse) {
				putInVerseBuffer(DOC_START);
				isFirstVerse = false;
				return;
			}
		
			if (verseIterator.hasNext()) {
				Verse currentVerse = verseIterator.next();
				//get the actual verse text and tidy it up
				String verseText = book.getRawText(currentVerse);
				verseText = osisVerseTidy.tidy(currentVerse, verseText);
				putInVerseBuffer(verseText);
				return;
			}
			
			if (!isClosingTagWritten) {
				putInVerseBuffer("</div>");
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
