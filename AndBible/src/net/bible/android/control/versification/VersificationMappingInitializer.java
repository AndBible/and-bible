package net.bible.android.control.versification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.BooksEvent;
import org.crosswire.jsword.book.BooksListener;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.VersificationsMapper;

import android.util.Log;

public class VersificationMappingInitializer {
	
	private static final String TAG = "VersificationMappingInitializer";
	
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
