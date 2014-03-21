package net.bible.android.control.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

public class DocumentBibleBooks {

	private List<BibleBook> bookList;
	private AbstractPassageBook document;
	
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
			if (document.contains(new Verse(documentVersification, bibleBook, 1, 1)) ||
				document.contains(new Verse(documentVersification, bibleBook, 1, 2)) ||
				document.contains(new Verse(documentVersification, bibleBook, 2, 2))) {
				bookList.add(bibleBook);
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

}
