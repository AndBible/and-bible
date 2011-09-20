package org.crosswire.jsword.book.sword;

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
	}
	
	public void testActivate() {
		Book finney = Books.installed().getBook("Finney");
		Book inst = Books.installed().getBook("Institutes");
		finney.getGlobalKeyList();
		finney.deactivate(null);
		inst.getGlobalKeyList();		
		finney.deactivate(null);
		inst.getGlobalKeyList();		
	}
	
	public void testTDavidContains() throws Exception {
		Book tdavid = Books.installed().getBook("TDavid");
		Verse verse = VerseFactory.fromString("Prov 19:14");
		assertFalse("TDavid contains not working correctly 1", tdavid.contains(verse));
		
		verse = VerseFactory.fromString("Isaiah 55:1");
		assertFalse("TDavid contains not working correctly 2", tdavid.contains(verse));
	}
}
