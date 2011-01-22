package net.bible.android.control.page;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.ChoosePassageBook;
import net.bible.android.view.activity.navigation.GridChoosePassageBook;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleInfo;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentBiblePage extends CurrentPageBase implements CurrentPage {
	
	private CurrentBibleVerse currentBibleVerse;

	private static final String TAG = "CurrentBiblePage";
	
	
	/* default */ CurrentBiblePage(CurrentBibleVerse currentVerse) {
		// share the verse holder with the CurrentCommentaryPage
		this.currentBibleVerse = currentVerse;
	}

	public BookCategory getBookCategory() {
		return BookCategory.BIBLE;
	}

	public Class getKeyChooserActivity() {
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		boolean gridNav = preferences.getBoolean("grid_navigation_pref", true);
		if (gridNav) {
			return GridChoosePassageBook.class;
		} else {
			return ChoosePassageBook.class;
		}
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
	@Override
	public void next() {
		Log.d(TAG, "Next");
		beforePageChange();
		nextChapter();
		pageChange();
	}
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
	@Override
	public void previous() {
		Log.d(TAG, "Previous");
		beforePageChange();
		previousChapter();
		pageChange();
	}
	
	private void nextChapter() {
		Verse currVer = this.currentBibleVerse.getVerseSelected();
		currentBibleVerse.setVerseSelected(new Verse(currVer.getBook(), currVer.getChapter()+1, 1, true));
	}
	
	private void previousChapter() {
		Verse currVer = this.currentBibleVerse.getVerseSelected();
		int book = currVer.getBook();
		int chapter = currVer.getChapter();
		try {
			if (chapter>1) {
				chapter--;
			} else {
				if (book>1) {
					book--;
					chapter = BibleInfo.chaptersInBook(book);
				}
			}
			currentBibleVerse.setVerseSelected(new Verse(book, chapter, 1, true));
		} catch (NoSuchVerseException nve) {
			Log.e(TAG, "No such verse moving to prev chapter", nve);
		}
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#setKey(java.lang.String)
	 */
	public void setKey(String keyText) {
		Log.d(TAG, "key text:"+keyText);
		try {
			Key key = getCurrentDocument().getKey(keyText);
			setKey(key);
		} catch (NoSuchKeyException nske) {
			Log.e(TAG, "Invalid verse reference:"+keyText);
		}
	}

	/** set key without notification
	 * 
	 * @param key
	 */
	protected void doSetKey(Key key) {
		Log.d(TAG, "Bible key set to:"+key);
		if (key!=null) {
			Verse verse = KeyUtil.getVerse(key);
			currentBibleVerse.setVerseSelected(verse);
		}
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getSingleKey()
	 */
	@Override
	public Key getSingleKey() {
		return doGetKey(true);
    }
	
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
		return doGetKey(false);
    }

	private Key doGetKey(boolean requireSingleKey) {
		Verse verse = currentBibleVerse.getVerseSelected();
		if (verse!=null) {
			Key key;
			if (!requireSingleKey) {
				// display whole page of bible so return whole chapter key - not just teh single verse even if a single verse was set in verseKey
				// if verseNo is required too then use getVerse()
		        Key wholeChapterKey = new VerseRange(verse.getFirstVerseInChapter(), verse.getLastVerseInChapter());
		        key = wholeChapterKey;
			} else {
				key = verse;
			}
			return key;
		} else {
			return new Verse(1,1,1, true);
		}
    }

	@Override
	public boolean isSingleKey() {
		return false;
	}
	
	public int getCurrentVerseNo() {
		return currentBibleVerse.getVerseNo();
	}
	public void setCurrentVerseNo(int verse) {
		currentBibleVerse.setVerseNo(verse);
		pageDetailChange();
	}

	public boolean isSingleChapterBook() throws NoSuchKeyException{
    	return BibleInfo.chaptersInBook(currentBibleVerse.getCurrentBibleBookNo())==1;
	}
	
	public int getNumberOfVersesDisplayed() {
		int numVerses = currentBibleVerse.getVerseSelected().getLastVerseInChapter().getVerse();
		return numVerses;
	}

	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	@Override
	public void saveState(SharedPreferences outState) {
		if (getCurrentDocument()!=null && currentBibleVerse!=null && currentBibleVerse.getVerseSelected()!=null) {
			SharedPreferences.Editor editor = outState.edit();
			editor.putString("document", getCurrentDocument().getInitials());
			editor.putInt("bible-book", currentBibleVerse.getCurrentBibleBookNo());
			editor.putInt("chapter", currentBibleVerse.getVerseSelected().getChapter());
			editor.putInt("verse", currentBibleVerse.getVerseNo());
			editor.commit();
		}
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	@Override
	public void restoreState(SharedPreferences inState) {
		if (inState!=null) {
			Log.d(TAG, "State not null");
			String document = inState.getString("document", null);
			if (StringUtils.isNotEmpty(document)) {
				Log.d(TAG, "State document:"+document);
				Book book = SwordApi.getInstance().getDocumentByInitials(document);
				if (book!=null) {
					Log.d(TAG, "Document:"+book.getName());
					// bypass setter to avoid automatic notifications
					localSetCurrentDocument(book);
				}
			}

			// bypass setter to avoid automatic notifications
			int bibleBookNo =  inState.getInt("bible-book", 1);
			int chapterNo = inState.getInt("chapter", 1);
			int verseNo = inState.getInt("verse", 1);
			Log.d(TAG, "Restored verse:"+bibleBookNo+"."+chapterNo+"."+verseNo);
			Verse verse = new Verse(bibleBookNo, chapterNo, verseNo, true);
			this.currentBibleVerse.setVerseSelected(verse);

			Log.d(TAG, "Current passage:"+toString());
		} 
		// force an update here from default chapter/verse
		pageChange();
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
		// by default disable notes but bible will enable
		menu.findItem(R.id.notes).setVisible(true);
	}

}