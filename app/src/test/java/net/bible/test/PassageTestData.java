package net.bible.test;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

public class PassageTestData {

	public static Book ESV;
	public static Versification KJV_V11N;
	public static Verse PS_139_2;

	static {
		ESV = Books.installed().getBook("ESV2011");
		KJV_V11N = Versifications.instance().getVersification("KJV");
		PS_139_2 = new Verse(KJV_V11N, BibleBook.PS, 139, 2);
	}

}
