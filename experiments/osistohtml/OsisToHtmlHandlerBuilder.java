package net.bible.service.format.osistohtml;

import java.util.HashMap;
import java.util.Map;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.xml.sax.ContentHandler;

public class OsisToHtmlHandlerBuilder {
	
	public static ContentHandler createOsisToHtmlHandler(Book book, Key key) {
		//TODO too many statics make this handler not thread safe so create some parts for each call
		return new CustomHandler(book, key);
	}
}
