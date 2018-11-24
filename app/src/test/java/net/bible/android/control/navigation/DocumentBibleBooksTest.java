package net.bible.android.control.navigation;

import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DocumentBibleBooksTest {

	@Test
	public void testContains() {
		AbstractPassageBook esv = (AbstractPassageBook)Books.installed().getBook("ESV2011");
		DocumentBibleBooks esvBibleBooks = new DocumentBibleBooks(esv);
		assertThat(true, is(esvBibleBooks.contains(BibleBook.GEN)));
		assertThat(true, is(esvBibleBooks.contains(BibleBook.OBAD)));
		assertThat(false, is(esvBibleBooks.contains(BibleBook.PR_AZAR)));
	}
}
