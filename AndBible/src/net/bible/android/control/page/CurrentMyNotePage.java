package net.bible.android.control.page;

import net.bible.android.view.activity.mynote.MyNotes;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.util.Log;

/** Provide information for My Note page
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentMyNotePage extends CurrentCommentaryPage implements CurrentPage {
	
	private static final String TAG = "CurrentMyNotePage";
	
	
	/* default */ CurrentMyNotePage(CurrentBibleVerse currentVerse) {
		super(currentVerse);
		// this doc does not use the notification mechanism
		setInhibitChangeNotifications(true);
	}

	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return false;
	}
}