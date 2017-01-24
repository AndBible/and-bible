package net.bible.android.control.page;

import android.app.Activity;
import android.util.Log;

import net.bible.android.view.activity.navigation.GridChoosePassageBook;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.json.JSONException;
import org.json.JSONObject;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentCommentaryPage extends VersePage implements CurrentPage {
	
	private static final String TAG = "CurrentCommentaryPage";
	
	/* default */ CurrentCommentaryPage(CurrentBibleVerse currentBibleVerse, SwordContentFacade swordContentFacade) {
		super(true, currentBibleVerse, swordContentFacade);
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
		onVerseChange();
	}
	
	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return true;
	}

	/** called during app close down to save state
	 */
	@Override
	public JSONObject getStateJson() throws JSONException {
		JSONObject object = new JSONObject();
		if (getCurrentDocument()!=null && getCurrentBibleVerse()!=null && getCurrentBibleVerse().getVerseSelected(getVersification())!=null) {
			Log.d(TAG, "Saving Commentary state for 1 window");
			object.put("document", getCurrentDocument().getInitials());
			// allow Bible page to save shared verse
		}
		return object;
	}
	
	/** called during app start-up to restore previous state
	 * 
	 * @param jsonObject
	 */
	@Override
	public void restoreState(JSONObject jsonObject) throws JSONException {
		if (jsonObject!=null) {
			Log.d(TAG, "Restoring Commentary page state");
			if (jsonObject.has("document")) {
				String document = jsonObject.getString("document");
				if (StringUtils.isNotEmpty(document)) {
					Book book = SwordDocumentFacade.getInstance().getDocumentByInitials(document);
					if (book!=null) {
						Log.d(TAG, "Restored document:"+book.getName());
						// bypass setter to avoid automatic notifications
						localSetCurrentDocument(book);
	
						// allow Bible page to restore shared verse
					}
				}
			}
		}
	}
}