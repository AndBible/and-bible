package org.crosswire.jsword.book.sword;

import java.io.File;
import java.io.PrintStream;

import junit.framework.TestCase;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public class BookTest extends TestCase {

	public BookTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		for (File file : SwordBookPath.getSwordPath()) {
			System.out.println("Sword book path:"+file.getAbsolutePath());
		}
	}
	
	public void testActivate() {
		Book finney = Books.installed().getBook("Finney");
		Book inst = Books.installed().getBook("Institutes");
		if (finney!=null && inst!=null) {
			finney.getGlobalKeyList();
			finney.deactivate(null);
			inst.getGlobalKeyList();		
			finney.deactivate(null);
			inst.getGlobalKeyList();
		}
	}
	
	public void testTDavidContains() throws Exception {
		// this is permanently broken - it seems
//		Book tdavid = Books.installed().getBook("TDavid");
//		Verse verse = VerseFactory.fromString(Versifications.instance().getVersification(null), "Prov 19:14");
//		assertFalse("TDavid contains not working correctly 1", tdavid.contains(verse));
//		
//		verse = VerseFactory.fromString(Versifications.instance().getVersification(null), "Isaiah 55:1");
//		assertFalse("TDavid contains not working correctly 2", tdavid.contains(verse));
	}

	public void testOSMHBContains() throws Exception {
		// this is permanently broken - it seems
		Book osmhb = Books.installed().getBook("OSMHB");
		Verse ntVerse = VerseFactory.fromString(((SwordBook)osmhb).getVersification(), "Matt 1:1");
		assertFalse("OSMHB contains NT book", osmhb.contains(ntVerse));
	}
	
	public void testRusSynodal() throws Exception {
		SwordBook book = (SwordBook)Books.installed().getBook("RusSynodal");
		assertNotNull("RusSynodal not found", book);
		Verse verse = VerseFactory.fromString(book.getVersification(), "Ps 1:6");
		PrintStream ps = new PrintStream(System.out, true, "UTF-8");
		ps.println(book.getRawText(verse));
	}

	public void testESVIs53() throws Exception {
		final String version = "ESV";
	    final String ref = "Is.53";
	    final Book currentBook = Books.installed().getBook(version);
	    final Key refKey = currentBook.getKey(ref);
	
	    final BookData bookData = new BookData(currentBook, refKey);
	    final Element osisFragment = bookData.getOsisFragment();
	
	    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getRawFormat());
	    xmlOutputter.outputString(osisFragment);
	    
	    assertTrue("Is 53 is empty", xmlOutputter.outputString(osisFragment).length()>100);
		// fails on the next line
	    assertTrue("Is 53 not found", currentBook.contains(refKey));
	}
	
	public void testStrongsCheck() {
		Book kjv = Books.installed().getBook("KJV");
		assertTrue("KJV not recognised as containing Strongs numbers", kjv.hasFeature(FeatureType.STRONGS_NUMBERS));
		Book osmhb = Books.installed().getBook("OSMHB");
		assertTrue("OSMHB GlobalOptionFilter hack does not find Strongs", osmhb.getBookMetaData().getProperty("GlobalOptionFilter").toString().contains("Strongs"));
		// failure on next line
		assertTrue("OSMHB not recognised as containing Strongs numbers", osmhb.hasFeature(FeatureType.STRONGS_NUMBERS) || osmhb.getBookMetaData().hasFeature(FeatureType.STRONGS_NUMBERS));
	}
	
	private VerseRange getWholeChapter(Verse currentVerse) {
		Versification versification = currentVerse.getVersification();
		BibleBook book = currentVerse.getBook();
		int chapter = currentVerse.getChapter();

		Verse targetChapterFirstVerse = new Verse(versification, book, chapter, 0);
		Verse targetChapterLastVerse = new Verse(versification, book, chapter, versification.getLastVerse(book, chapter));
		
		// convert to full chapter before returning because bible view is for a full chapter
		return new VerseRange(versification, targetChapterFirstVerse, targetChapterLastVerse);
	}
}
