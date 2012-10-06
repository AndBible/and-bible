package net.bible.service.sword;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import net.bible.service.format.FormattedDocument;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.xml.SAXEventProvider;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.BibleInfo;

public class SwordApiTest extends TestCase {

	private Book[] books;
	private SwordDocumentFacade swordDocumentFacade;

	protected void setUp() throws Exception {
		super.setUp();
		SwordDocumentFacade.isAndroid = false;
		SwordDocumentFacade.setAndroid(false);
		swordDocumentFacade = SwordDocumentFacade.getInstance();
		
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
	
	public void testReadPolBibTysia() throws Exception {
		Book esv = getBook("PolBibTysia");
		Key key = PassageKeyFactory.instance().getKey("Gen 1");
		
		String html = getHtml(esv, key, 100);
		System.out.println(html);
//		assertTrue("key text missing", html.contains("searched and inquired carefully,"));
//		assertTrue("key text missing", html.contains("inquiring what person or time"));
//		System.out.println(html);
	}

	public void testReadWSCKeys() throws Exception {
		Book book = getBook("Westminster");
		Key keyList = book.getGlobalKeyList();
		
		System.out.println("Global key list");
		for (Key key : keyList) {
			String rawText = book.getRawText(key);
			System.out.println(key.getName() + " has "+rawText.length()+" chars content");
		}
		
		String badKey = "Q91-150";
		Key key = book.getKey(badKey);
		BookData data = new BookData(book, key);		
		SAXEventProvider osissep = data.getSAXEventProvider();

	}

	public void testReadPilgrimKeys() throws Exception {
		Book book = getBook("Pilgrim");
		Key globalKeyList = book.getGlobalKeyList();
		
		System.out.println("Global key list");
		for (Key key : globalKeyList) {
			if (StringUtils.isNotEmpty(key.getName())) {
				String rawText = book.getRawText(key);
				System.out.println(key.getName() + " has "+rawText.length()+" chars content");
				if (rawText.length()<30) {
					System.out.println(rawText);
				}
			}
		}
    	assertEquals("Incorrect number of keys in master list", 29, globalKeyList.getCardinality());
		
		System.out.println("\nChildren");
    	for (int i=0; i<globalKeyList.getChildCount(); i++) {
    		System.out.println(globalKeyList.get(i));
    	}
    	assertEquals("Incorrect number of top level keys", 6, globalKeyList.getChildCount());
	}

	public void testGetTags() throws Exception {
		Book book = getBook("TS1998");

		Key key = book.getKey("matt.25.6");
		System.out.println(book.getRawText(key));

		key = book.getKey("matt.25.7");
		System.out.println(book.getRawText(key));
	}
	
	public void testComparePilgrimKeys() throws Exception {
		Book book = getBook("Pilgrim");
		
		// flatten and cache all the keys 
		List<Key> cachedKeyList = new ArrayList<Key>();
		for (Key key : book.getGlobalKeyList()) {
			cachedKeyList.add(key);
		}
		// get Part II/First Stage key
		Key partIIFirstStage = cachedKeyList.get(20);
    	assertEquals("wrong key 20", "THE FIRST STAGE", partIIFirstStage.getName());
    	assertEquals("wrong key 20", "PART II", partIIFirstStage.getParent().getName());
		
    	// now try to find the above key in our cached list of keys but the wrong key is returned
    	int indexOfKey = cachedKeyList.indexOf(partIIFirstStage);
    	// this test fails because Part I/First Stage is returned instead of Part II/First Stage
    	assertEquals("Wrong index", 20, indexOfKey);
	}

	public void testReadHodgeMissingKey() throws Exception {
		Book book = getBook("HodgeSysTheo");
		Key volume1Key = book.getKey("Volume I");
		assertNotNull("Vol 1 not found", volume1Key);
		if (!book.contains(volume1Key)) {
			System.out.println("Book does not contain a valid key");
		}
	}

	public void testReadDodsonKey() throws Exception {
		Book book = Books.installed().getBook("Dodson");
		assertNotNull("Dodson not installed", book);
		assertEquals("Dodson incorrectly installed", book.getInitials(), "Dodson");
		
		Key allKeys = book.getGlobalKeyList();
		assertTrue("Global key list problem in Dobsons", allKeys.getCardinality()>1000);
		
		Key firstKey = allKeys.get(0);
		assertEquals("Incorrect first key", "G0001", firstKey.getName());
		// all the below fail
		assertEquals("Cannot getKey G0001", "G0001", book.getKey("G0001").getName()); // G0002 is returned
		assertEquals("Cannot getKey G0009", "G0009", book.getKey("G0009").getName()); // G0002 is returned
		assertTrue("G0001 not in Dodson", book.contains(firstKey)); // false is returned
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

	public void testReadPilgrimsProgress1() throws Exception {
		Book book = getBook("Pilgrim");
		{
			Key key = book.getKey("THE FIRST STAGE");
			BookData data = new BookData(book, key);		
			SAXEventProvider osissep = data.getSAXEventProvider();

			String html = getHtml(book, key, 100);
			System.out.println(html);
		}
	}

	public void testReadPilgrimsProgress2() throws Exception {
		try {
			Book book = getBook("Pilgrim");
			Key key = book.getKey("THE FIRST STAGE");
			key.getOsisID();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void testFindInJosephus() throws Exception {
		try {
			Book book = getBook("Josephus");
			assertNotNull("Josephus not available", book);
			
			// find a key and print out it's name - this works
			final String SECTION_2 = "Section 2";
			Key key = book.getKey(SECTION_2);
			assertEquals(SECTION_2, key.getName());
			
			// but we can't get it's index - this returns -1
			int keyPos = book.getGlobalKeyList().indexOf(key);
			assertFalse("Could not get index of a valid key", -1==keyPos);

		} catch (Exception e) {
			e.printStackTrace();
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
		
		String html = SwordContentFacade.getInstance().getCanonicalText(esv, key);
		assertEquals("Wrong canonical text", html, "In the beginning, God created the heavens and the earth.");
		System.out.println(html);
	}

	private String getHtml(Book book, Key key, int maxVerses) throws Exception {
		FormattedDocument formattedDocument = SwordContentFacade.getInstance().readHtmlText(book, key);
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
		System.out.println("Looking for "+initials);
		for (Book book : books) {
			if (book.getInitials().equals(initials)) {
				System.out.println("*** Found:"+book.getName());
				return book;
			}
		}
		return null;
	}
	
	public void testCheckISVVersesExist() {
		Book isv = Books.installed().getBook("ISV");
    	for (BibleBook book: EnumSet.range(BibleBook.GEN, BibleBook.REV)) {
    		System.out.println(book);
    		try {
	    		for (int chap=1; chap<=BibleInfo.chaptersInBook(book); chap++ ) {
	    			for (int verse=1; verse <= BibleInfo.versesInChapter(book, chap); verse++) {
				        Key key = isv.getKey(new Verse(book, chap, verse).getOsisID());
				        BookData data = new BookData(isv, key);
				        String plainText = OSISUtil.getCanonicalText(data.getOsisFragment());
				        if (plainText.isEmpty()) {
				        	System.out.println("Missing:"+key.getOsisID());
				        }
	    			}
	    		}
    		} catch (Exception e) {
    			System.out.println("missing verse");
    		}
    		
    	}

	}

}
