package net.bible.android.control.page;

import net.bible.android.activity.ChoosePassageBook;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;

import android.content.SharedPreferences;
import android.util.Log;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentCommentaryPage extends CurrentPageBase implements CurrentPage {
	
	private CurrentBibleVerse currentBibleVerse;

	private static final String TAG = "CurrentBiblePage";
	
	
	/* default */ CurrentCommentaryPage(CurrentBibleVerse currentVerse) {
		// share the verse holder with the CurrentBiblePage
		this.currentBibleVerse = currentVerse;
	}

	public BookCategory getBookCategory() {
		return BookCategory.COMMENTARY;
	}

	public Class getKeyChooserActivity() {
		return ChoosePassageBook.class;
	}

	public String getKeyDescription() {
		return getCurrentDocument().getInitials()+" "+currentBibleVerse.getVerseSelected().toString();
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
	@Override
	public void next() {
		Log.d(TAG, "Next");
		nextVerse();
		pageChange();
	}
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
	@Override
	public void previous() {
		Log.d(TAG, "Previous");
		previousVerse();
		pageChange();
	}
	
	private void nextVerse() {
		Verse currVer = this.currentBibleVerse.getVerseSelected();
		currentBibleVerse.setVerseSelected(currVer.add(1));
	}
	private void previousVerse() {
		Verse currVer = this.currentBibleVerse.getVerseSelected();
		currentBibleVerse.setVerseSelected(currVer.add(-1));
	}
	
	/** set key without notification
	 * 
	 * @param key
	 */
	protected void doSetKey(Key key) {
		if (key!=null) {
			Verse verse = KeyUtil.getVerse(key);
			currentBibleVerse.setVerseSelected(verse);
		}
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
		Log.i(TAG, "getKey:"+currentBibleVerse.getVerseSelected());
		return currentBibleVerse.getVerseSelected();
    }

	public boolean isSingleChapterBook() throws NoSuchKeyException{
    	return BibleInfo.chaptersInBook(currentBibleVerse.getCurrentBibleBookNo())==1;
	}
	
	public int getNumberOfVersesDisplayed() {
		return 1;
	}

	@Override
	public boolean isSingleKey() {
		return true;
	}
	public int getCurrentVerse() {
		return currentBibleVerse.getVerseNo();
	}
	public void setCurrentVerse(int verse) {
		currentBibleVerse.setVerseNo(verse);
		pageDetailChange();
	}
	
	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	@Override
	public void saveState(SharedPreferences outState) {
		//TODO save this too
		
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	@Override
	public void restoreState(SharedPreferences inState) {
		//TODO restore this too
	}
}