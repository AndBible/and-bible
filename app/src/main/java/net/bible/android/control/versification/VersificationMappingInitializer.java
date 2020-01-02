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

package net.bible.android.control.versification;

import android.util.Log;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.BooksEvent;
import org.crosswire.jsword.book.BooksListener;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.VersificationsMapper;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class VersificationMappingInitializer {
	
	private static final String TAG = "VersificatnMappingInit";
	
	public void startListening() {
		Books.installed().addBooksListener(new BooksListener() {

			@Override
			public void bookAdded(BooksEvent ev) {
				Book book = ev.getBook();
				initialiseRequiredMapping(book);
			}

			@Override
			public void bookRemoved(BooksEvent ev) {
				//NOOP
			}
		});
	}
	
	/**
	 *  pre-initialise mappings to prevent pauses during interaction
	 */
	private synchronized void initialiseRequiredMapping(Book book) {
		if (book instanceof SwordBook) {
			final Versification versification = ((SwordBook)book).getVersification();
			// initialise in a background thread to allow normal startup to continue
			new Thread(	new Runnable() {
				public void run() {
					Log.d(TAG, "AVMAP Initialise v11n mappings for "+versification.getName());
					VersificationsMapper.instance().ensureMappingDataLoaded(versification);
				}
			}).start();
		}
	}
}
