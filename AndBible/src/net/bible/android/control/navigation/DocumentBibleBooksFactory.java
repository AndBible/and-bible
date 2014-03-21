package net.bible.android.control.navigation;

import java.util.List;

import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;

public class DocumentBibleBooksFactory {
	
	public DocumentBibleBooks getDocumentBibleBooksFor(AbstractPassageBook document) {
		return new DocumentBibleBooks(document);
	}

	public List<BibleBook> getBooksFor(AbstractPassageBook document) {
		return getDocumentBibleBooksFor(document).getBookList();
	}
}
