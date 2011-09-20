/**
 * 
 */
package net.bible.service.format;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import junit.framework.TestCase;
import net.bible.service.common.ParseException;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler;

import org.crosswire.common.xml.SAXEventProvider;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;
import org.xml.sax.ContentHandler;


/**
 * @author denha1m
 *
 */
public class OSISInputStreamTest extends TestCase {

	private Book[] books;
	private Book netBook;
	private Book webBook;
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
        SwordBookDriver swordBookDriver = new SwordBookDriver();
        books = swordBookDriver.getBooks();
		for (Book book : books) {
			if (book.getInitials().startsWith("NET")) {
				this.netBook = book;
			}
			if (book.getInitials().startsWith("WEB")) {
				this.webBook = book;
			}
//			System.out.println(book.getOsisID());
		}
	}

	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link net.bible.service.format.scripture.service.sword.OSISInputStream#read()}.
	 * <!DOCTYPE div [<!ENTITY nbsp "&#160;"><!ENTITY copy "&#169;">]><div><verse osisID='Gen.1.1'/><w lemma="strong:H07225">In the begin
ning</w> <w lemma="strong:H0430">God</w> <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w> <w lemma="st
rong:H08064">the heaven</w> <w lemma="strong:H0853">and</w> <w lemma="strong:H0776">the earth</w>.
</div>
	 */
	public void testReadKJV() throws Exception {
		Book kjv = getBook("KJV");

//		OSISInputStream osisInputStream = new OSISInputStream(kjv, kjv.getKey("Is 40:11"));
//		OSISInputStream osisInputStream = new OSISInputStream(kjv, kjv.getKey("Mt 4:14"));
		OSISInputStream osisInputStream = new OSISInputStream(kjv, kjv.getKey("Ps 67:1"));
		String chapter = convertStreamToString(osisInputStream);
//		int numOpeningDivs = count(chapter, "<div>");
//		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
	}

	public void testReadTitle() throws Exception {
		Book book = getBook("WEB");

		OSISInputStream osisInputStream = new OSISInputStream(book, book.getKey("Hosea 1:2"));
		String verse = convertStreamToString(osisInputStream);
		System.out.println(verse);
		
		osisInputStream = new OSISInputStream(book, book.getKey("Psalm 36:1"));
		verse = convertStreamToString(osisInputStream);
		System.out.println(verse);
		
		
		
	}

	public void testReadABPGRKJn1() throws Exception {
		Book kjv = getBook("ABP");

		OSISInputStream osisInputStream = new OSISInputStream(kjv, kjv.getKey("Jn 1:1"));
		String chapter = convertStreamToString(osisInputStream);
//		int numOpeningDivs = count(chapter, "<div>");
//		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
	}

	/**
	 * Test method for {@link net.bible.service.format.scripture.service.sword.OSISInputStream#read()}.
	 */
	public void testReadNetBible() throws Exception {
		OSISInputStream osisInputStream = new OSISInputStream(netBook, netBook.getKey("Gen 4"));
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
	}
	public void testReadLastChapter() throws Exception {
		for (Book book: books) {
			if (book.getBookCategory().equals(BookCategory.BIBLE)) {
				System.out.println("Book:"+book.getInitials());
				OSISInputStream osisInputStream = new OSISInputStream(book, book.getKey("3 John"));
				String chapter = convertStreamToString(osisInputStream);
				int numOpeningDivs = count(chapter, "<div>");
				int numClosingDivs = count(chapter, "</div>");
	//			System.out.println(chapter);
				assertEquals("wrong number of divs in "+book, numOpeningDivs, numClosingDivs);
			}
		}
	}

	public void testReadWordsOfChrist() throws Exception {
		Book esv = getBook("ESV");

		OSISInputStream osisInputStream = new OSISInputStream(esv, esv.getKey("Luke 14:3"));
		String chapter = convertStreamToString(osisInputStream);
//		int numOpeningDivs = count(chapter, "<div>");
//		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
//		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
	}

	/**
	 * Test method for {@link net.bible.service.format.scripture.service.sword.OSISInputStream#read()}.
	 */
	public void testReadSingleChapterBook() throws Exception {
		OSISInputStream osisInputStream = new OSISInputStream(netBook, netBook.getKey("3 John"));
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
	}
	
	/**
	 * Test method for {@link net.bible.service.format.scripture.service.sword.OSISInputStream#read()}.
	 */
	public void testReadVeryLongBook() throws Exception {
		Book esv = getBook("ESV");
		OSISInputStream osisInputStream = new OSISInputStream(esv, esv.getKey("Ps 119"));
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
	}

	public void testTrickyWebChapters() throws Exception {
		{
			OSISInputStream osisInputStream = new OSISInputStream(webBook, PassageKeyFactory.instance().getKey("Ps 1"));
			String chapter = convertStreamToString(osisInputStream);
			int numOpeningLs = count(chapter, "<l>") + count(chapter, "<l ");
			int numClosingLs = count(chapter, "</l>");
			System.out.println(chapter);
			assertEquals("wrong number of Ls", numOpeningLs, numClosingLs);
		}
		{
			OSISInputStream osisInputStream = new OSISInputStream(webBook, PassageKeyFactory.instance().getKey("Gen 49"));
			String chapter = convertStreamToString(osisInputStream);
			int numOpeningLs = count(chapter, "<l>") + count(chapter, "<l ");
			int numClosingLs = count(chapter, "</l>");
			System.out.println(chapter);
			assertEquals("wrong number of Ls", numOpeningLs, numClosingLs);

		}
	}

	public void testReadDarbyBible() throws Exception {
		Book book = getBook("Darby");
		OSISInputStream osisInputStream = new OSISInputStream(book, book.getKey("Gen 1"));
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
	}
	
	public void testReadRST() throws Exception {
		Book rst = getBook("RST");
		BookData data = new BookData(rst, rst.getKey("Col 1"));
		SAXEventProvider osissep = data.getSAXEventProvider();

		// canonical
		try {

			if (osissep != null) {
//				OsisToHtmlSaxHandler osisToHtml = new OsisToHtmlSaxHandler();
				ContentHandler osisToHtml = new OsisToCanonicalTextSaxHandler();

				osissep.provideSAXEvents(osisToHtml);
		
				String chapter = osisToHtml.toString();
				System.out.println(chapter);
			}		
		} catch (Exception e) {
			fail("Parsing error");
			throw new ParseException("Parsing error", e);
		}

		//html
		try {

			if (osissep != null) {
				ContentHandler osisHandler = new OsisToHtmlSaxHandler(new OsisToHtmlParameters());

				osissep.provideSAXEvents(osisHandler);
		
				String chapter = osisHandler.toString();
				System.out.println(chapter);
			}		
		} catch (Exception e) {
			fail("Parsing error");
			throw new ParseException("Parsing error", e);
		}

//		
//		OSISInputStream osisInputStream = new OSISInputStream(rst, rst.getKey("Col 1:1"));
//		String chapter = convertStreamToString(osisInputStream);
//		int numOpeningDivs = count(chapter, "<div>");
//		int numClosingDivs = count(chapter, "</div>");
//		System.out.println(chapter);
	}

	public void testReadESVAndBibeMethod() throws Exception {
		Book book = getBook("ESV");
		OSISInputStream osisInputStream = new OSISInputStream(book, book.getKey("Phil 1:3"));
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println("START"+chapter+"END");
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
	}

	public void testReadESVJSwordMethod() throws Exception {
		Book esv = getBook("ESV");
		BookData data = new BookData(esv, esv.getKey("Phil 1:3"));
		SAXEventProvider osissep = data.getSAXEventProvider();

		// canonical
		try {

			if (osissep != null) {
//				OsisToHtmlSaxHandler osisToHtml = new OsisToHtmlSaxHandler();
				ContentHandler osisToHtml = new OsisToCanonicalTextSaxHandler();

				osissep.provideSAXEvents(osisToHtml);
		
				String chapter = osisToHtml.toString();
				System.out.println("START"+chapter+"END");
			}		
		} catch (Exception e) {
			fail("Parsing error");
			throw new ParseException("Parsing error", e);
		}

		//html
		try {

			if (osissep != null) {
				ContentHandler osisHandler = new OsisToHtmlSaxHandler(new OsisToHtmlParameters());

				osissep.provideSAXEvents(osisHandler);
		
				String chapter = osisHandler.toString();
				System.out.println(chapter);
			}		
		} catch (Exception e) {
			fail("Parsing error");
			throw new ParseException("Parsing error", e);
		}

//		
//		OSISInputStream osisInputStream = new OSISInputStream(rst, rst.getKey("Col 1:1"));
//		String chapter = convertStreamToString(osisInputStream);
//		int numOpeningDivs = count(chapter, "<div>");
//		int numClosingDivs = count(chapter, "</div>");
//		System.out.println(chapter);
	}

	public void testReadCommentaries() throws Exception {
		for (Book book: books) {
			System.out.println("?Book:"+book);
			if (book.getBookCategory().equals(BookCategory.COMMENTARY) && !book.getInitials().equals("Personal")) {
//			if (book.getInitials().equals("JFB")) {
				System.out.println("Book:"+book.getInitials());
				OSISInputStream osisInputStream = new OSISInputStream(book, book.getKey("1 Cor 1:1"));
				String chapter = convertStreamToString(osisInputStream);
				int numOpeningDivs = count(chapter, "<div>");
				int numClosingDivs = count(chapter, "</div>");
				System.out.println(chapter);
				assertEquals("wrong number of divs in "+book, numOpeningDivs, numClosingDivs);
			}
		}
	}
	
	public void testRead1Peter1v10() throws Exception {
		Book book = getBook("WEB");
		OSISInputStream osisInputStream = new OSISInputStream(book, book.getKey("1 Peter 1"));
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
		System.out.println(chapter);
	}

	public void testReadStrongs() throws Exception {
		Book book = Defaults.getHebrewDefinitions();
		assertEquals(book.getInitials(), "StrongsHebrew");
		Key key = book.getKey("00430");
		
		OSISInputStream osisInputStream = new OSISInputStream(book, key);
		String chapter = convertStreamToString(osisInputStream);
		int numOpeningDivs = count(chapter, "<div>");
		int numClosingDivs = count(chapter, "</div>");
		System.out.println(chapter);
		assertEquals("wrong number of divs", numOpeningDivs, numClosingDivs);
		System.out.println(chapter);
	}

	public void testFindAllStrongsRef() throws Exception {
		List<Book> bibles = Books.installed().getBooks(BookFilters.getBibles());
		
		for (Book book : bibles) {
			try {
				if (book.hasFeature(FeatureType.STRONGS_NUMBERS)) {
					if (!book.getIndexStatus().equals(IndexStatus.DONE)) {
						System.out.println("Unindexed:"+book);
					} else {
						Key resultsH = book.find("+[Gen 1:1] strong:h7225"); //beginning
						Key resultsG = book.find("+[John 1:1] strong:g746"); //beginning
						Key resultsGOT = book.find("+[Gen 1:1] strong:g746"); //beginning
						if (resultsH.getCardinality()==0 && resultsG.getCardinality()==0 && resultsGOT.getCardinality()==0) {
							System.out.println("No refs returned in"+book.getInitials());
						} else {
							System.out.println("Ok:"+book.getInitials()+" "+resultsH.getCardinality()+"/"+resultsG.getCardinality()+ "/" + resultsGOT.getCardinality());
						}
	//					assertTrue("No refs returned in"+book.getInitials(), resultsH.getCardinality()>0 || resultsG.getCardinality()>0);
					}
				}
			} catch (Exception e) {
				System.out.println("Error:"+book.getInitials()+":"+e.getMessage());
			}
		}
	}

	private Book getKJV() {
		return getBook("KJV");
	}
	private Book getBook(String initials) {
		for (Book book : books) {
			if (book.getInitials().equals(initials)) {
				System.out.println("Found:"+book.getName());
				return book;
			}
		}
		return null;
	}
	
	
//	/**
//	 * Test method for {@link org.crosswire.jsword.book.sword.OSISInputStream#OSISInputStream(org.crosswire.jsword.book.Book, org.crosswire.jsword.passage.Key)}.
//	 */
//	public void testOSISInputStream() {
//		fail("Not yet implemented");
//	}
//
//	/**
//	 * Test method for {@link java.io.InputStream#available()}.
//	 */
//	public void testAvailable() {
//		fail("Not yet implemented");
//	}
//
//	/**
//	 * Test method for {@link java.io.InputStream#read(byte[])}.
//	 */
//	public void testReadByteArray() {
//		fail("Not yet implemented");
//	}
//
//	/**
//	 * Test method for {@link java.io.InputStream#read(byte[], int, int)}.
//	 */
//	public void testReadByteArrayIntInt() {
//		fail("Not yet implemented");
//	}

    public String convertStreamToString(InputStream is) throws IOException {
        /*
         * To convert the InputStream to String we use the BufferedReader.readLine()
         * method. We iterate until the BufferedReader return null which means
         * there's no more data to read. Each line will appended to a StringBuilder
         * and returned as String.
         */
        if (is != null) {
            StringBuilder sb = new StringBuilder();
            String line;

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } finally {
                is.close();
            }
            return sb.toString();
        } else {        
            return "";
        }
    }
    
    private int count(String base, String searchFor) {
        int len   = searchFor.length();
        int result = 0;
      
        if (len > 0) {  // search only if there is something
            int start = base.indexOf(searchFor);
            while (start != -1) {
                result++;
                start = base.indexOf(searchFor, start+len);
            }
        }
        return result;
    }
    
}
