package net.bible.android.currentpagecontrol;

import java.util.List;

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

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentBiblePage extends CurrentPageBase implements CurrentPage {
	
	private Book currentDocument;
	private CurrentBibleVerse currentBibleVerse;

	private static final String TAG = "CurrentBiblePage";
	
	
	/* default */ CurrentBiblePage(CurrentBibleVerse currentVerse) {
		//TODO shouldn't need to do this here
		List<Book> books = SwordApi.getInstance().getDocuments();
		if (books.size()>0) {
			currentDocument = books.get(0);
			Log.i(TAG, "Initial book:"+currentDocument.getInitials());
		}
		this.currentBibleVerse = currentVerse;
	}

	public String getKeyDescription() {
		return currentDocument.getInitials()+" "+currentBibleVerse.getVerseSelected().toString();
	}


	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#next()
	 */
	@Override
	public void next() {
		Log.d(TAG, "Next");
		nextChapter();
		pageChange();
	}
	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#previous()
	 */
	@Override
	public void previous() {
		Log.d(TAG, "Previous");
		previousChapter();
		pageChange();
	}
	
	private void nextChapter() {
		Verse currVer = this.currentBibleVerse.getVerseSelected();
		currentBibleVerse.setVerseSelected(new Verse(currVer.getBook(), currVer.getChapter()+1, 1, true));
	}
	
	private void previousChapter() {
		Verse currVer = this.currentBibleVerse.getVerseSelected();
		currentBibleVerse.setVerseSelected(new Verse(currVer.getBook(), currVer.getChapter()-1, 1, true));
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#setKey(java.lang.String)
	 */
	public void setKey(String keyText) {
		Log.d(TAG, "key text:"+keyText);
		try {
			Key key = currentDocument.getKey(keyText);
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
		Verse verse = KeyUtil.getVerse(key);
		currentBibleVerse.setVerseSelected(verse);
	}

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
		Verse verse = currentBibleVerse.getVerseSelected();
        Key wholeChapterKey = new VerseRange(verse.getFirstVerseInChapter(), verse.getLastVerseInChapter());

        return wholeChapterKey;
    }

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#getCurrentDocument()
	 */
	@Override
	public Book getCurrentDocument() {
		if (currentDocument==null) {
			List<Book> bibles = SwordApi.getInstance().getBibles();
			if (bibles.size()>0) {
				currentDocument = bibles.get(0);
			}
		}
		return currentDocument;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocument(Book currentBible) {
		this.currentDocument = currentBible;
		pageChange();
	}

	public int getCurrentBibleBookNo() {
		return currentBibleVerse.getVerseSelected().getBook();
	}

	public boolean isSingleChapterBook() throws NoSuchKeyException{
    	return BibleInfo.chaptersInBook(currentBibleVerse.getCurrentBibleBookNo())==1;
	}
	
	public int getNumberOfVersesDisplayed() {
		int numVerses = currentBibleVerse.getVerseSelected().getLastVerseInChapter().getVerse();
		return numVerses;
	}

	@Override
	public boolean isSingleKey() {
		return false;
	}
	public int getCurrentVerse() {
		return currentBibleVerse.getVerseSelected().getVerse();
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
		//TODO xxxtodo save verse instead of individual numbers
		SharedPreferences.Editor editor = outState.edit();
		editor.putString("document", getCurrentDocument().getInitials());
		editor.putInt("bible-book", currentBibleVerse.getCurrentBibleBookNo());
		editor.putInt("chapter", currentBibleVerse.getVerseSelected().getChapter());
		editor.putInt("verse", getCurrentVerse());
		editor.commit();
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
					this.currentDocument = book;
				}
			}

			// bypass setter to avoid automatic notifications
			int bibleBookNo =  inState.getInt("bible-book", 1);
			int chapter = inState.getInt("chapter", 1);
			int verse = inState.getInt("verse", 1);
			this.currentBibleVerse.setVerseSelected(new Verse(bibleBookNo, chapter, verse, true));

			Log.d(TAG, "Current passage:"+toString());
		} 
		// force an update here from default chapter/verse
		pageChange();
		pageDetailChange();
	}
}