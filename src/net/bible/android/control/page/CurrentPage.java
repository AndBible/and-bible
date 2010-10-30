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

	public abstract void setKey(Key key);

	public abstract boolean isSingleKey();

	public abstract Key getKey();
	
	public abstract String getKeyDescription();

	public abstract Book getCurrentDocument();

	public abstract void setCurrentDocument(Book currentBible);

	public void setCurrentDocumentAndKey(Book doc, Key key);
	
	public abstract void updateOptionsMenu(Menu menu);

	public abstract void restoreState(SharedPreferences inState);

	public abstract void saveState(SharedPreferences outState);

	public abstract void setInhibitChangeNotifications(boolean inhibitChangeNotifications);

	public abstract boolean isInhibitChangeNotifications();

	public abstract boolean isSearchable();
}