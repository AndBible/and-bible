package net.bible.android.currentpagecontrol;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;

public interface CurrentPage {

	public abstract String toString();

	public abstract void next();

	public abstract void previous();

	public abstract void setKey(Key key);

	public abstract boolean isSingleKey();

	public abstract Key getKey();
	
	public abstract String getKeyDescription();

	public abstract Book getCurrentDocument();

	public abstract void setCurrentDocument(Book currentBible);

	public abstract void restoreState(SharedPreferences inState);

	public abstract void saveState(SharedPreferences outState);

}