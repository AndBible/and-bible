/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.navigation;

import androidx.collection.LruCache;

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
