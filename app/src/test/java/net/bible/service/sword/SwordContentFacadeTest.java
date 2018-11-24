package net.bible.service.sword;

import net.bible.android.TestBibleApplication;
import net.bible.service.format.usermarks.BookmarkFormatSupport;
import net.bible.service.format.usermarks.MyNoteFormatSupport;
import net.bible.test.DatabaseResetter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import robolectric.MyRobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@RunWith(MyRobolectricTestRunner.class)
@Config(application = TestBibleApplication.class)
public class SwordContentFacadeTest {

	private SwordContentFacade swordContentFacade;

	@Before
	public void setUp() throws Exception {
		swordContentFacade = new SwordContentFacade(new BookmarkFormatSupport(), new MyNoteFormatSupport());
	}

	@After
	public void finishComponentTesting() {
		DatabaseResetter.resetDatabase();
	}

	@Test
	public void testReadFragment() throws Exception {
		Book esv = getBook("ESV2011");
		Key key = PassageKeyFactory.instance().getKey(((SwordBook)esv).getVersification(), "John 11:35");

		String html = getHtml(esv, key, true);
		assertThat(html, not(containsString("<html")));
	}

	@Test
	public void testReadWordsOfChrist() throws Exception {
		Book esv = getBook("ESV2011");
		Key key = PassageKeyFactory.instance().getKey(((SwordBook)esv).getVersification(), "Luke 15:4");
		
		String html = getHtml(esv, key, false);
		assertThat(html, containsString("“What <a href='gdef:05101' class='strongs'>5101</a>  man <a href='gdef:00444' class='strongs'>444</a>  of <a href='gdef:01537' class='strongs'>1537</a>  you <a href='gdef:05216' class='strongs'>5216</a> , having <a href='gdef:02192' class='strongs'>2192</a>  a hundred <a href='gdef:01540' class='strongs'>1540</a>  sheep"));
	}

	@Test
	public void testReadCanonicalText() throws Exception {
		Book esv = getBook("ESV2011");
		Key key = PassageKeyFactory.instance().getKey(((SwordBook)esv).getVersification(), "Gen 1:1");
		
		String html = swordContentFacade.getCanonicalText(esv, key);
		assertThat("Wrong canonical text", html, equalTo("In the beginning, God created the heavens and the earth. "));
	}

	private String getHtml(Book book, Key key, boolean asFragment) throws Exception {
		return swordContentFacade.readHtmlText(book, key, asFragment);
	}

	private Book getBook(String initials) {
		System.out.println("Looking for "+initials);
		return Books.installed().getBook(initials);
	}
}
