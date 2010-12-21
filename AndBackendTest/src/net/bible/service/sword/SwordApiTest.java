package net.bible.service.sword;

import java.util.Map;

import junit.framework.TestCase;
import net.bible.service.format.FormattedDocument;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;

public class SwordApiTest extends TestCase {

	private Book[] books;
	private SwordApi swordApi;

	protected void setUp() throws Exception {
		super.setUp();
		SwordApi.setAndroid(false);
		swordApi = SwordApi.getInstance();
		
        SwordBookDriver swordBookDriver = new SwordBookDriver();
        books = swordBookDriver.getBooks();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

//	public void testGetBibles() {
//		fail("Not yet implemented");
//	}

	public void testGetAbout() {
		Book jfb = getJFB();
		Map props = jfb.getBookMetaData().getProperties();
		String about = (String)props.get("About");
		about = about.replace("\\par", "\n\n");
		System.out.println(about);

		for (Object prop : props.keySet()) {
			System.out.println(prop);
		}

	}
	public void testReadHtmlText() throws Exception {
		Book jfb = getJFB();
		Key key = PassageKeyFactory.instance().getKey("Mat 1:1");
		
		String html = getHtml(jfb, key, 100);
		System.out.println(html);
	}

	public void testReadPsalm119() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey("Ps 119");
		
		String html = getHtml(esv, key, 200);
		System.out.println(html);
	}

	/** my esv on my mobile was corrupted and showed incorrect biblical text in I Peter 1, but while researching I added this junit
	 * 
	 * @throws Exception
	 */
	public void testPsalmNoVerse() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey("Ps 2");
		
		String html = getHtml(esv, key, 100);
		System.out.println(html);
		System.out.println(html);

	
	}

	/** my esv on my mobile was corrupted and showed incorrect biblical text in I Peter 1, but while researching I added this junit
	 * 
	 * @throws Exception
	 */
	public void testRead1Peter1v10() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey("1 Pet 1");
		
		String html = getHtml(esv, key, 100);
		System.out.println(html);
		assertTrue("key text missing", html.contains("searched and inquired carefully,"));
		assertTrue("key text missing", html.contains("inquiring what person or time"));
		System.out.println(html);

	
	}

	public void testReadWordsOfChrist() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey("Luke 15:3-8");
		
		String html = getHtml(esv, key, 100);
		System.out.println(html);
//		assertTrue("key text missing", html.contains("searched and inquired carefully,"));
//		assertTrue("key text missing", html.contains("inquiring what person or time"));
//		System.out.println(html);
	}

	public void testReadTrickyWEBChaptersText() throws Exception {
		Book web = getWEB();
		{
			Key key = PassageKeyFactory.instance().getKey("Ps 1");
			String html = getHtml(web, key, 100);
			System.out.println(html);
		}
		{
			Key key2 = PassageKeyFactory.instance().getKey("Gen 49");
			String html2 = getHtml(web, key2, 100);
			System.out.println(html2);
		}
	}

	public void testReadDarby() throws Exception {
		Book book = getBook("Darby");
		Key key = PassageKeyFactory.instance().getKey("Gen 1");
		String html = getHtml(book, key, 100);
		System.out.println(html);
	}

	public void testReadCanonicalText() throws Exception {
		Book esv = getBook("ESV");
		Key key = PassageKeyFactory.instance().getKey("Gen 1:1");
		
		String html = swordApi.getCanonicalText(esv, key);
		assertEquals("Wrong canonical text", html, "In the beginning, God created the heavens and the earth.");
		System.out.println(html);
	}

	private String getHtml(Book book, Key key, int maxVerses) throws Exception {
		FormattedDocument formattedDocument = swordApi.readHtmlText(book, key, 100);
		String html = formattedDocument.getHtmlPassage();
		return html;		
	}
	private Book getJFB() {
		for (Book book : books) {
			if (book.getInitials().equals("JFB")) {
				System.out.print("Found:"+book.getName());
				return book;
			}
		}
		return null;
	}
	private Book getWEB() {
		for (Book book : books) {
			if (book.getInitials().equals("WEB")) {
				System.out.print("Found:"+book.getName());
				return book;
			}
		}
		return null;
	}
	private Book getBook(String initials) {
		for (Book book : books) {
			System.out.println(book.getName());
			if (book.getInitials().equals(initials)) {
				System.out.println("*** Found:"+book.getName());
				return book;
			}
		}
		return null;
	}
}
