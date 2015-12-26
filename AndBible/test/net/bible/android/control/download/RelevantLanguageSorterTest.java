package net.bible.android.control.download;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bible.service.download.FakeSwordBookFactory;

import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;
import org.junit.Test;

public class RelevantLanguageSorterTest {

	private RelevantLanguageSorter relevantLanguageSorter;
	
	@Test
	public void testCompare() throws Exception {
		Locale.setDefault(Locale.KOREAN);
		
		Language svInstalledLang = new Language("sv");
		Book svInstalledBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "Lang=sv", null);
		List<Book> books = new ArrayList<Book>();
		books.add(svInstalledBook);
		relevantLanguageSorter = new RelevantLanguageSorter(books);

		Language frPopularLang = new Language("fr");
		Language koDefaultLang = new Language("ko");
		Language inNotRelevantLang = new Language("in");
		Language fiNotRelevantLang = new Language("fi");

		// both relevant: installed book and major language
		assertThat(relevantLanguageSorter.compare(svInstalledLang, frPopularLang), greaterThan(0));
		assertThat(relevantLanguageSorter.compare(frPopularLang, svInstalledLang), lessThan(0));

		// both relevant: default language
		assertThat(relevantLanguageSorter.compare(koDefaultLang, frPopularLang), greaterThan(0));
		assertThat(relevantLanguageSorter.compare(frPopularLang, koDefaultLang), lessThan(0));

		// One relevant
		assertThat(relevantLanguageSorter.compare(koDefaultLang, inNotRelevantLang), lessThan(0));

		// Neither relevant
		assertThat(relevantLanguageSorter.compare(fiNotRelevantLang, inNotRelevantLang), lessThan(0));
		assertThat(relevantLanguageSorter.compare(inNotRelevantLang, fiNotRelevantLang), greaterThan(0));
	}
}
