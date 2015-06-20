package net.bible.test;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;

public class PassageTestData {

	public static Book ESV;
	public static Key PS_139_2;

	static {
		try {
			ESV = Books.installed().getBook("ESV");
			PS_139_2 = ESV.getKey("Ps.139.2");
		} catch (NoSuchKeyException nske) {
			nske.printStackTrace();
		}
	}

}
