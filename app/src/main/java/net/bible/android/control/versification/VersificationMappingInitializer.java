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
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
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
