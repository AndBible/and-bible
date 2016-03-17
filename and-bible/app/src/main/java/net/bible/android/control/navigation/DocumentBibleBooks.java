package net.bible.android.control.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.bible.android.control.versification.Scripture;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractBook;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemSynodal;

/**
 * Calculate which books are actually included in a Bible document.
 * Necessary for boks with v11n like Synodal but without dc books eg IBT.
 * Useful for partial documents eg NT or WIP.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentBibleBooks {

	private List<BibleBook> bookList;
	private AbstractPassageBook document;
	private boolean onlyScripture = true;
	
	private Boolean isProbablyIBT = null;
	private static final int IBT_EMPTY_VERSE_STUB_MIN_LENGTH = "<chapter eID=\"gen4\" osisID=\"Gen.1\"/>".length();
	private static final int IBT_EMPTY_VERSE_STUB_MAX_LENGTH = "<chapter eID=\"gen1146\" osisID=\"1Macc.1\"/>".length();
	private static final int IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MIN_LENGTH = "<chapter eID=\"gen955\" osisID=\"Obad.1\"/> <div eID=\"gen954\" osisID=\"Obad\" type=\"book\"/> <div eID=\"gen953\" type=\"x-Synodal-empty\"/>".length();
	private static final int IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MAX_LENGTH = "<chapter eID=\"gen1136\" osisID=\"EpJer.1\"/> <div eID=\"gen1135\" osisID=\"EpJer\" type=\"book\"/> <div eID=\"gen1134\" type=\"x-Synodal-non-canonical\"/>".length();
	
	private static Scripture scripture = new Scripture();

	@SuppressWarnings("unused")
	private static final String TAG = "DocumentBibleBooks";
	
	public DocumentBibleBooks(AbstractPassageBook document) {
		this.document = document;
		calculateBibleBookList();
	}
	
	/** 
	 * Iterate all books checking if document contains a verse from the book
	 */
	private void calculateBibleBookList() {
		List<BibleBook> bookList = new ArrayList<BibleBook>();
		
		// iterate over all book possible in this document
		Versification documentVersification = document.getVersification(); 
		Iterator<BibleBook> v11nBookIterator = documentVersification.getBookIterator();
		
		while (v11nBookIterator.hasNext()) {
			BibleBook bibleBook = v11nBookIterator.next();
			// test some random verses - normally ch1 v 1 is sufficient - but we don't want to miss any
			if (isVerseInBook(document, documentVersification, bibleBook, 1, 1) || 
				isVerseInBook(document, documentVersification, bibleBook, 1, 2)) {
				bookList.add(bibleBook);
				
				onlyScripture &= scripture.isScripture(bibleBook);
			}
		}
		
		this.bookList = bookList;
	}
	
	public boolean contains(BibleBook book) {
		return bookList.contains(book);
	}
	
	public List<BibleBook> getBookList() {
		return Collections.unmodifiableList(bookList);
	}

	public boolean isOnlyScripture() {
		return onlyScripture;
	}

	public void setContainsOnlyScripture(boolean containsOnlyScripture) {
		this.onlyScripture = containsOnlyScripture;
	}

	private boolean isVerseInBook(Book document, Versification v11n, BibleBook bibleBook, int chapter, int verseNo ) {
		Verse verse = new Verse(v11n, bibleBook, chapter, verseNo);
		
		// no content for specified verse implies this verse clearly is not in this document
		if (!document.contains(verse)) {
			return false;
		}
		
		// IBT Synodal documents sometimes return stub data for missing verses in dc books e.g. <chapter eID="gen7" osisID="1Esd.1"/>
		if (isProbablyIBTDocument(document, v11n) && isProbablyEmptyVerseInDocument(document, verse)) {
			// it is just IBT dummy empty verse content 
			return false;
		}
		
		return true; 
	}

	/** IBT books are Synodal but are known to have mistakenly added empty verses for all dc books
	 *  Here we check to see if this document probably has that problem.
	 */
	private boolean isProbablyIBTDocument(Book document, Versification v11n) {
		if (isProbablyIBT==null) {
			isProbablyIBT = SystemSynodal.V11N_NAME.equals(v11n.getName()) &&
							isProbablyEmptyVerseInDocument(document, new Verse(v11n, BibleBook.TOB, 1, 1));
		}
		return isProbablyIBT;
	}
	
	/**
	 * Some IBT books mistakenly had dummy empty verses which returned the following for verse 1,2,... lastVerse-1  
	 * <chapter eID="gen7" osisID="1Esd.1"/>
	 * <chapter eID="gen1010" osisID="Mal.3"/>
	 */
	private boolean isProbablyEmptyVerseInDocument(Book document, Verse verse) {
		int rawTextLength = ((AbstractBook)document).getBackend().getRawTextLength(verse);
		
		if (verse.getBook().isShortBook()) {
			return isProbablyShortBookEmptyVerseStub(rawTextLength);
		} else {
			return isProbablyEmptyVerseStub(rawTextLength);
		}
	}
	
	/** There is a standard type of tag padding in each empty verse that has a fairly predictable length
	 */
	private boolean isProbablyEmptyVerseStub(int rawTextLength) {
		return rawTextLength >= IBT_EMPTY_VERSE_STUB_MIN_LENGTH &&
				rawTextLength <= IBT_EMPTY_VERSE_STUB_MAX_LENGTH;
	}
	
	/** 1 chapter books have a different type of empty verse stub that includes the end of chapter tag
	 * <chapter eID="gen955" osisID="Obad.1"/> <div eID="gen954" osisID="Obad" type="book"/> <div eID="gen953" type="x-Synodal-empty"/>
	 */
	private boolean isProbablyShortBookEmptyVerseStub(int rawTextLength) {
		return rawTextLength >= IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MIN_LENGTH &&
				rawTextLength <= IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MAX_LENGTH;
	}
}
