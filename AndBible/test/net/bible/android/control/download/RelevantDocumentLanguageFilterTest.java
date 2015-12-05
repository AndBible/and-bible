package net.bible.android.control.download;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bible.android.control.download.RelevantDocumentLanguageFilter;
import net.bible.service.download.FakeSwordBookFactory;

import org.crosswire.jsword.book.Book;
import org.junit.Test;

public class RelevantDocumentLanguageFilterTest {

	private RelevantDocumentLanguageFilter relevantDocumentLanguageFilter;
	
	@Test
	public void testTest() throws Exception {
		Locale.setDefault(Locale.KOREAN);
		
		Book hiBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "Lang=hi", null);
		List<Book> books = new ArrayList<Book>();
		books.add(hiBook);
		relevantDocumentLanguageFilter = new RelevantDocumentLanguageFilter(books);

		// true for languages of installed books
		assertThat(relevantDocumentLanguageFilter.test(hiBook), equalTo(true));

		// true for major languages
		Book frBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "Lang=fr", null);
		assertThat(relevantDocumentLanguageFilter.test(frBook), equalTo(true));

		// true for default language
		Book koBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "Lang=ko", null);
		assertThat(relevantDocumentLanguageFilter.test(koBook), equalTo(true));

		// false for other languages
		Book inBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "Lang=in", null);
		assertThat(relevantDocumentLanguageFilter.test(inBook), equalTo(false));
	}
}
