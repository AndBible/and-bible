package net.bible.android.control.navigation;

import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class DocumentBibleBooksFactoryTest {

	private DocumentBibleBooksFactory documentBibleBooksFactory;
	private AbstractPassageBook esv;

	@Before
	public void setup() {
		documentBibleBooksFactory = new DocumentBibleBooksFactory();
		esv = (AbstractPassageBook) Books.installed().getBook("ESV2011");
	}

	@After
	public void tearDown() {
		// ensure it is in the list after removal by some tests
		Books.installed().addBook(esv);
	}

	@Test
	public void initialise_shouldInstallBookChangeListenersToResetCache() throws Exception {
		documentBibleBooksFactory.initialise();
		assertThat(documentBibleBooksFactory.size(), equalTo(0));

		documentBibleBooksFactory.getBooksFor(esv);
		assertThat(documentBibleBooksFactory.size(), equalTo(1));

		Books.installed().removeBook(esv);
		assertThat(documentBibleBooksFactory.size(), equalTo(0));
	}

	@Test
	public void getDocumentBibleBooksFor() throws Exception {
		final List<BibleBook> esvBibleBooks = documentBibleBooksFactory.getBooksFor(esv);
		assertThat(documentBibleBooksFactory.size(), equalTo(1));
		assertThat(true, is(esvBibleBooks.contains(BibleBook.GEN)));
	}

	@Test
	public void getBooksFor() throws Exception {
		final List<BibleBook> esvBibleBooks = documentBibleBooksFactory.getBooksFor(esv);
		assertThat(true, is(esvBibleBooks.contains(BibleBook.GEN)));
	}
}