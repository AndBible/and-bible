package net.bible.service.format;

import java.util.Iterator;
import java.util.Locale;

import junit.framework.TestCase;
import net.bible.android.TestUtil;
import net.bible.service.sword.OSISInputStream;

import org.crosswire.common.xml.SAXEventProvider;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.AccuracyType;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.RestrictionType;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class HandleScripRef extends TestCase {
	
	/**
public static AccuracyType fromText(String original, String[] parts, AccuracyType verseAccuracy, VerseRange basis) throws NoSuchVerseException {
basis is the current verse to use in case of no book or chapter
        assertEquals(AccuracyType.fromText("Gen 1:1", AccuracyType.tokenize("Gen 1:1"), vr), AccuracyType.BOOK_VERSE);
        assertEquals(AccuracyType.fromText("Gen 1", AccuracyType.tokenize("Gen 1"), vr), AccuracyType.BOOK_CHAPTER);
        assertEquals(AccuracyType.fromText("Jude 1", AccuracyType.tokenize("Jude 1"), vr), AccuracyType.BOOK_VERSE);
        assertEquals(AccuracyType.fromText("Jude 1:1", AccuracyType.tokenize("Jude 1:1"), vr), AccuracyType.BOOK_VERSE);
        assertEquals(AccuracyType.fromText("Gen", AccuracyType.tokenize("Gen"), vr), AccuracyType.BOOK_ONLY);
        assertEquals(AccuracyType.fromText("1:1", AccuracyType.tokenize("1:1"), vr), AccuracyType.CHAPTER_VERSE);
        assertEquals(AccuracyType.fromText("1", AccuracyType.tokenize("1"), vr), AccuracyType.VERSE_ONLY);

	 */
	public void testKeyRepresentations() {
		try {
			Key key = PassageKeyFactory.instance().getKey("Gen 1,2,4:3");
			assertEquals("Genesis 1-2, 4:3", key.toString());
			assertEquals("Gen.1-Gen.2 Gen.4.3", key.getOsisRef());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** TSK is Thml format and contains scripRef tags but must be converted to OSIS reference tags
	 */
	public void testJSwordThmlProcessing() throws Exception  {
		Book book = Books.installed().getBook("TSK");
		Key key = book.getKey("Matt 21:21");
		BookData data = new BookData(book, key);		
		SAXEventProvider osissep = data.getSAXEventProvider();
		
		final StringBuilder tags = new StringBuilder();
		
		ContentHandler saxHandler = new DefaultHandler() {
			@Override
			public void startElement(String uri, String localName,	String qName, Attributes attributes) throws SAXException {
				tags.append("<"+qName+">");
			}
		};

		osissep.provideSAXEvents(saxHandler);
		String html = tags.toString();
		System.out.println(html);
		assertTrue("Thml not converted to OSIS", html.contains("reference") && !html.contains("scripRef"));

	}

	public void testSwordApiTSKProcessing() throws Exception  {
		Book book = TestUtil.getBook("TSK");
		Key key = book.getKey("Gen 1:1");
		
		String html = TestUtil.getHtml(book, key, 100);
		System.out.println(html);
		assertTrue("Link to bible not found", html.contains("<a href='bible:Gen"));
	}

	public void testForeignSwordApiTSKProcessing() throws Exception  {
		Locale.setDefault(Locale.GERMAN);
		Book book = TestUtil.getBook("TSK");
		Key key = book.getKey("Matt 22:3");
		
		String html = TestUtil.getHtml(book, key, 100);
		System.out.println(html);
//		assertTrue("Link to bible not found", html.contains("<a href='bible:Gen"));
	}
	
	public void testSwordApiTSKProcessingWithNoBookAtStart() throws Exception  {
		Book book = TestUtil.getBook("TSK");
		Key key = book.getKey("Matt 21:21");
		// this ref is 17:20; Mr 11:22,23; Lu 17:6,7; Ro 4:19,20; 1Co 13:2; Jas 1:6 i.e. with no book name at start
		
		String html = TestUtil.getHtml(book, key, 100);
		System.out.println(html);
		assertTrue("Link to bible not found", html.contains("<a href='bible:M"));
	}

	public void testTsk() {
		try {
			Book tsk = TestUtil.getBook("TSK");
			System.out.println(tsk.getRawText(tsk.getKey("Matt 21:21")));
			
			// Refer to ScripRefTag for example
			// must handle either <scripRef passage="Ge 1:3">3</scripRef>
			// or <scripRef>Pr 8:22-24; 16:4; Mr 13:19; Joh 1:1-3; Heb 1:10; 1Jo 1:1</scripRef>
//			String testRef = "Pr 8:22-24; 16:4; Mr 13:19; Joh 1:1-3; Heb 1:10; 1Jo 1:1";
//			String testRef = "Prov.8.22-Prov.8.24 Prov.16.4 Mark.13.19 John.1.1-John.1.3 Heb.1.10 1John.1.1";
			String testRef = "17:20; Mr 11:22,23; Lu 17:6,7; Ro 4:19,20; 1Co 13:2; Jas 1:6";
			// error - book is missing
//			String[] parts = testRef.split(";");
//			for (String part : parts) {
//				System.out.println(PassageKeyFactory.instance().getKey(part));
//			}

            Passage ref = (Passage) PassageKeyFactory.instance().getKey(testRef);
			System.out.println("osis ref"+ref.getOsisRef());
			System.out.println("count ranges"+ref.countRanges(RestrictionType.CHAPTER));
			System.out.println("count"+ref.getCardinality());

			Iterator<Key> it = ref.rangeIterator(RestrictionType.CHAPTER);
			while (it.hasNext()) {
				Key key = it.next();
				System.out.println("Range (show this):"+key);
				System.out.println("Start (go to this):"+key.iterator().next());
			}
			
			for (Key k : ref) {
				System.out.println(k);
			}
            String osisname = ref.getOsisRef();

			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void testNote() throws Exception {
		Book esv = TestUtil.getBook("ESV");
		Key gen11 = esv.getKey("Gen 1:1");
		String text = esv.getRawText(gen11);
		System.out.println(text);
	}

	public void testStrongsRef() throws Exception {
		{
		Book strongs = TestUtil.getBook("StrongsHebrew");
		Key key = strongs.getKey("00778");
		String text = strongs.getRawText(key);
		System.out.println(text);
		}
		{
		Book strongs = TestUtil.getBook("StrongsGreek");
		Key key = strongs.getKey("01252");
		String text = strongs.getRawText(key);
		System.out.println(text);
		}
	}

}
