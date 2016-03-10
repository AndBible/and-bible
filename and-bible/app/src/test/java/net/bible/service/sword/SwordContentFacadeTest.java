package net.bible.service.sword;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SwordContentFacadeTest {

	private SwordContentFacade swordContentFacade;

	@Before
	public void setUp() throws Exception {
		swordContentFacade = SwordContentFacade.getInstance();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testReadWordsOfChrist() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey(((SwordBook)esv).getVersification(), "Luke 15:4");
		
		String html = getHtml(esv, key, 100);
		assertThat(html, containsString("What man of you, having a hundred sheep,"));
	}


	@Test
	public void testReadCanonicalText() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey(((SwordBook)esv).getVersification(), "Gen 1:1");
		
		String html = swordContentFacade.getCanonicalText(esv, key);
		assertThat("Wrong canonical text", html, equalTo("In the beginning, God created the heavens and the earth. "));
	}

	private String getHtml(Book book, Key key, int maxVerses) throws Exception {
		String html = swordContentFacade.readHtmlText(book, key);
		return html;		
	}

	private Book getBook(String initials) {
		System.out.println("Looking for "+initials);
		return Books.installed().getBook(initials);
	}
}
