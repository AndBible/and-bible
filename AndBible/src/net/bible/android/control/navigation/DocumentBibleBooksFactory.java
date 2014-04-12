package net.bible.android.control.navigation;

import java.util.List;

import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;

/**
 * Factory for {@link DocumentBibleBooks}.
 * Should eventually cache {@link DocumentBibleBooks}
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentBibleBooksFactory {
	
	public DocumentBibleBooks getDocumentBibleBooksFor(AbstractPassageBook document) {
		return new DocumentBibleBooks(document);
	}

	public List<BibleBook> getBooksFor(AbstractPassageBook document) {
		return getDocumentBibleBooksFor(document).getBookList();
	}
}
