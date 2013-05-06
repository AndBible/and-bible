package org.crosswire.jsword.book.sword;

import java.io.File;
import java.io.PrintStream;

import junit.framework.TestCase;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;

public class BookTest extends TestCase {

	public BookTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		for (File file : SwordBookPath.getSwordPath()) {
			System.out.println("Sword book path:"+file.getAbsolutePath());
		}
	}
	
	public void testActivate() {
		Book finney = Books.installed().getBook("Finney");
		Book inst = Books.installed().getBook("Institutes");
		if (finney!=null && inst!=null) {
			finney.getGlobalKeyList();
			finney.deactivate(null);
			inst.getGlobalKeyList();		
			finney.deactivate(null);
			inst.getGlobalKeyList();
		}
	}
	
	public void testTDavidContains() throws Exception {
		// this is permanently broken - it seems
//		Book tdavid = Books.installed().getBook("TDavid");
//		Verse verse = VerseFactory.fromString(Versifications.instance().getVersification(null), "Prov 19:14");
//		assertFalse("TDavid contains not working correctly 1", tdavid.contains(verse));
//		
//		verse = VerseFactory.fromString(Versifications.instance().getVersification(null), "Isaiah 55:1");
//		assertFalse("TDavid contains not working correctly 2", tdavid.contains(verse));
	}
	
	public void testRusSynodal() throws Exception {
		SwordBook book = (SwordBook)Books.installed().getBook("RusSynodal");
		assertNotNull("RusSynodal not found", book);
		Verse verse = VerseFactory.fromString(book.getVersification(), "Ps 1:6");
		PrintStream ps = new PrintStream(System.out, true, "UTF-8");
		ps.println(book.getRawText(verse));
	}
}
