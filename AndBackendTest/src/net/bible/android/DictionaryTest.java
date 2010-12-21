package net.bible.android;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import net.bible.service.format.FormattedDocument;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.bridge.BookInstaller;
import org.crosswire.jsword.passage.Key;

public class DictionaryTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testGetKeys()
	{
		Book bookg = Defaults.getGreekDefinitions();
		Key keys = bookg.getGlobalKeyList();
		System.out.println(keys.getName());
		Iterator iter = keys.iterator();
		while (iter.hasNext()) {
			Key key = (Key)iter.next();
			System.out.println(key.getName()+key.getClass());
		}
		
	}


	public void testGetGreekIndexes() {
		try {
			Book bookg = Defaults.getGreekDefinitions();
			Key keys = bookg.getGlobalKeyList();
			System.out.println(keys.getName());
			Iterator iter = keys.iterator();
			while (iter.hasNext()) {
				Key key = (Key)iter.next();
				System.out.println(key.getName()+key.getClass());
			}

			Key wordKey = bookg.getKey("03056");
	        BookData data = new BookData(bookg, wordKey);
	        System.out.println(OSISUtil.getPlainText(data.getOsisFragment())); //$NON-NLS-1$
	        System.out.println(SwordApi.getInstance().readHtmlText(bookg, wordKey, 1).getHtmlPassage());

//			Key wordKey2 = bookg.getKey("03004");
//	        BookData data2 = new BookData(bookg, wordKey2);
//	        System.out.println(OSISUtil.getPlainText(data2.getOsisFragment())); //$NON-NLS-1$


//	        Key first = (Key) keys.iterator().next();
//
//	        System.out.println("The first Key in the default dictionary is " + first); //$NON-NLS-1$
//
//	        BookData data = new BookData(bookg, first);
//	        System.out.println("And the text against that key is " + OSISUtil.getPlainText(data.getOsisFragment())); //$NON-NLS-1$

			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Strongs problem");
		}
	}
	
	public void testReadStrongs() throws Exception {
		Book book = Defaults.getHebrewDefinitions();
		assertEquals(book.getInitials(), "StrongsHebrew");
		Key key = book.getKey("00430");
		Key firstKey = book.getGlobalKeyList().get(0);
		String firstName = firstKey.getName();
		System.out.println("ist="+firstName);
		System.out.println(firstName.compareTo("00430"));
		
//		String html = getHtml(book, key, 100);
//		System.out.println(html);
	}

	public void testSearchStrongs() throws Exception {
//		Book book = Defaults.getHebrewDefinitions();
//		
//		Collections.binarySearch(arg0, arg1)
//		assertEquals(book.getInitials(), "StrongsHebrew");
//		Key key1 = book.getKey("00853");
//		Key key2 = book.getKey("01254");
//		Key key = book.createEmptyKeyList();
//		key.addAll(key1);
//		key.addAll(key2);
//		
//		String html = getHtml(book, key, 100);
//		System.out.println(html);
	}

	private Book getStrongsGreek() {
    	List<Book> books = new BookInstaller().getRepositoryBooks("CrossWire", BookFilters.getCustom("Feature=GreekDef"));
    	assertTrue("Could not get dictionary list", books.size()>0);
		System.out.println("Count:"+books.size());
		for (Book book : books) {
			if (book.getInitials().indexOf("Strong")!=-1) {
				System.out.println(book.getLanguage()+" init:"+book.getInitials()+" "+book.getBookMetaData().getProperty("SourceType")+book.getBookMetaData().getProperty("ModDrv")+" "+book.getDriverName()+" "+book.getBookMetaData().getKeyType()+" "+book.getName());
				return book;
			}
		}
		return null;
	}
	
	private String getHtml(Book book, Key key, int maxVerses) throws Exception {
		FormattedDocument formattedDocument = SwordApi.getInstance().readHtmlText(book, key, 100);
		String html = formattedDocument.getHtmlPassage();
		return html;		
	}

	private void usefulStuff() {
		Book bookg = Defaults.getGreekDefinitions();
		Book bookh = Defaults.getHebrewDefinitions();
	}

}
