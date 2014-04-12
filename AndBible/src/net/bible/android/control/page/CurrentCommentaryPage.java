package net.bible.android.control.page;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.navigation.GridChoosePassageBook;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentCommentaryPage extends VersePage implements CurrentPage {
	
	private static final String TAG = "CurrentCommentaryPage";
	
	/* default */ CurrentCommentaryPage(CurrentBibleVerse currentBibleVerse) {
		super(true, currentBibleVerse);
	}

	public BookCategory getBookCategory() {
		return BookCategory.COMMENTARY;
	}

	public Class<? extends Activity> getKeyChooserActivity() {
		return GridChoosePassageBook.class;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
	@Override
	public void next() {
		Log.d(TAG, "Next");
		nextVerse();
	}
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
	@Override
	public void previous() {
		Log.d(TAG, "Previous");
		previousVerse();
	}
	
	private void nextVerse() {
		setKey(getKeyPlus(1));
	}
	private void previousVerse() {
		setKey(getKeyPlus(-1));
	}
	
	/** add or subtract a number of pages from the current position and return Verse
	 */
	public Verse getKeyPlus(int num) {
		Versification v11n = getVersification();
		Verse currVer = this.getCurrentBibleVerse().getVerseSelected(v11n);

		try {
			Verse nextVer = currVer;
			if (num>=0) {
				// move to next book or chapter if required
				for (int i=0; i<num; i++) {
					nextVer = getBibleTraverser().getNextVerse(getCurrentPassageBook(), nextVer);
				}
			} else {
				// move to next book if required
				// allow standard loop structure by changing num to positive
				num = -num;
				for (int i=0; i<num; i++) {
					nextVer = getBibleTraverser().getPrevVerse(getCurrentPassageBook(), nextVer);
				}
			}
		
			return nextVer;
		} catch (Exception nsve) {
			Log.e(TAG, "Incorrect verse", nsve);
			return currVer;
		}
	}
	
	/** set key without notification
	 * 
	 * @param key
	 */
	public void doSetKey(Key key) {
		if (key!=null) {
			Verse verse = KeyUtil.getVerse(key);
			getCurrentBibleVerse().setVerseSelected(getVersification(), verse);
		}
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
		return getCurrentBibleVerse().getVerseSelected(getVersification());
    }

	public int getNumberOfVersesDisplayed() {
		return 1;
	}

	@Override
	public boolean isSingleKey() {
		return true;
	}
	public int getCurrentVerse() {
		return getCurrentBibleVerse().getVerseNo();
	}
	public void setCurrentVerse(int verse) {
		getCurrentBibleVerse().setVerseNo(verse);
		pageDetailChange();
	}
	
	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return true;
	}

	@Override
	public void updateContextMenu(Menu menu) {
		super.updateContextMenu(menu);
		// by default disable notes but bible and commentary will enable
		MenuItem myNotesMenuItem = menu.findItem(R.id.myNoteAddEdit);
		myNotesMenuItem.setVisible(true);
		myNotesMenuItem.setTitle(ControlFactory.getInstance().getMyNoteControl().getAddEditMenuText());
	}
}