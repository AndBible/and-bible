package net.bible.android.control.navigation;

import android.support.v4.util.LruCache;

import net.bible.android.control.ApplicationScope;
import net.bible.service.common.Logger;

import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.BooksEvent;
import org.crosswire.jsword.book.BooksListener;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;

import java.util.List;

import javax.inject.Inject;

/**
 * Caching factory for {@link DocumentBibleBooks}.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class DocumentBibleBooksFactory {
	
	private LruCache<AbstractPassageBook, DocumentBibleBooks> cache; 

	private Logger log = new Logger(this.getClass().getName());

	private static final int CACHE_SIZE = 10;

	@Inject
	public DocumentBibleBooksFactory() {
		// initialise the DocumentBibleBooks factory
		cache = new LruCache<AbstractPassageBook, DocumentBibleBooks>(CACHE_SIZE) {

			/** If entry for this Book not found in cache then create one
			 */
			@Override
			protected DocumentBibleBooks create(AbstractPassageBook document) {
				return new DocumentBibleBooks(document);
			}
		};

		initialise();
	}
	
	public void initialise() {
		log.debug("Initialising DocumentBibleBooksFactory cache");

		flushCacheIfBooksChange();
	}
	
	public DocumentBibleBooks getDocumentBibleBooksFor(AbstractPassageBook document) {
		return cache.get(document);
	}

	public List<BibleBook> getBooksFor(AbstractPassageBook document) {
		return getDocumentBibleBooksFor(document).getBookList();
	}

	public int size() {
		return cache.size();
	}

	/**
	 * Different versions of a Book may contain different Bible books so flush cache if a Book may have been updated
	 */
	private void flushCacheIfBooksChange() {
		Books.installed().addBooksListener(new BooksListener() {
			@Override
			public void bookAdded(BooksEvent ev) {
				cache.evictAll();
			}
			@Override
			public void bookRemoved(BooksEvent ev) {
				cache.evictAll();
			}
		});
	}
}
