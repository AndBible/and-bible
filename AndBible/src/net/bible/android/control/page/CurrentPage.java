package net.bible.android.control.page;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;
import android.view.Menu;

public interface CurrentPage {

	public abstract String toString();

	public abstract BookCategory getBookCategory();

	public abstract Class getKeyChooserActivity();

	public abstract void next();

	public abstract void previous();
	
	/** get incremented key according to the type of page displayed - verse, chapter, ...
	 */
	public abstract Key getKeyPlus(int num);

	/** add or subtract a number of pages from the current position and return Page
	 */
	public Key getPagePlus(int num);

	public abstract void setKey(Key key);

	public abstract boolean isSingleKey();
	
	// bible and commentary share a key (verse)
	public boolean isShareKeyBetweenDocs();

	/** get current key
	 */
	public abstract Key getKey();
	
	/** get key for 1 verse instead of whole chapter if bible
	 */
	public abstract Key getSingleKey();
	
	public abstract Book getCurrentDocument();

	public abstract void setCurrentDocument(Book currentBible);

	public void setCurrentDocumentAndKey(Book doc, Key key);
	
	public abstract void updateOptionsMenu(Menu menu);
	public abstract void updateContextMenu(Menu menu);

	public abstract void restoreState(SharedPreferences inState);

	public abstract void saveState(SharedPreferences outState);

	public abstract void setInhibitChangeNotifications(boolean inhibitChangeNotifications);

	public abstract boolean isInhibitChangeNotifications();

	public abstract boolean isSearchable();
}