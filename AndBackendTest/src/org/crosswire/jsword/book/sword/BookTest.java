package org.crosswire.jsword.book.sword;

import junit.framework.TestCase;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;

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

}
