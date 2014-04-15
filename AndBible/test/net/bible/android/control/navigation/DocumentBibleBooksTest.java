package net.bible.android.control.navigation;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import junit.framework.TestCase;

import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;

public class DocumentBibleBooksTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	public void testContains() {
		AbstractPassageBook esv = (AbstractPassageBook)Books.installed().getBook("ESV");
		DocumentBibleBooks esvBibleBooks = new DocumentBibleBooks(esv);
		assertThat(true, is(esvBibleBooks.contains(BibleBook.GEN)));
		assertThat(true, is(esvBibleBooks.contains(BibleBook.OBAD)));
		assertThat(false, is(esvBibleBooks.contains(BibleBook.PR_AZAR)));
	}
}
