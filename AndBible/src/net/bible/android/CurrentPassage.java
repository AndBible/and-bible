package net.bible.android;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;
import org.crosswire.jsword.versification.BookName;

import android.content.SharedPreferences;
import android.util.Log;

public class CurrentPassage extends Observable {
	private static CurrentPassage singleton;
	
	private Book currentDocument;
	private int currentBibleBookNo = 1;
	private int currentChapter = 1;
	private CurrentVerse currentVerse = new CurrentVerse();
	
	private static final String TAG = "SessionState";
	
	static public CurrentPassage getInstance() {
		if (singleton==null) {
			synchronized(CurrentPassage.class)  {
				if (singleton==null) {
					CurrentPassage instance = new CurrentPassage();
					singleton = instance;
				}
			}
		}
		return singleton;
	}
	
	private CurrentPassage() {
		List<Book> books = SwordApi.getInstance().getDocuments();
		if (books.size()>0) {
			currentDocument = books.get(0);
			Log.i(TAG, "Initial book:"+currentDocument.getInitials());
		}
	}

	@Override
	public String toString() {
		return currentDocument.getInitials()+" "+getCurrentBibleBook().getLongName()+" "+currentChapter+":"+getCurrentVerse();
	}

	public void next() {
		Log.d(TAG, "Next");
		if (isSingleVerse()) {
			nextVerse();
		} else {
			nextChapter();
		}
		notifyObserversOfChange();
	}
	public void previous() {
		Log.d(TAG, "Previous");
		if (isSingleVerse()) {
			previousVerse();
		} else {
			previousChapter();
		}
		notifyObserversOfChange();
	}
	
	private void nextChapter() {
		try {
			if (currentChapter<BibleInfo.chaptersInBook(currentBibleBookNo)) {
				currentChapter++;
			} else {
				if (currentBibleBookNo<BibleInfo.booksInBible()) {
					currentBibleBookNo++;
					currentChapter = 1;
				}
			}
		} catch (NoSuchVerseException nve) {
			Log.e(TAG, "No such verse moving to next chapter", nve);
		}
	}
	private void nextVerse() {
		Verse verse = KeyUtil.getVerse(getKey());
		verse = verse.add(1);
		Log.d(TAG, "Next verse:"+verse.getName());
		setKey(verse);
	}
	private void previousVerse() {
		Verse verse = KeyUtil.getVerse(getKey());
		verse = verse.add(-1);
		Log.d(TAG, "Prev verse:"+verse.getName());
		setKey(verse);
	}
	private void previousChapter() {
		try {
			if (currentChapter>1) {
				currentChapter--;
			} else {
				if (currentBibleBookNo>1) {
					currentBibleBookNo--;
					currentChapter = BibleInfo.chaptersInBook(currentBibleBookNo);
				}
			}
		} catch (NoSuchVerseException nve) {
			Log.e(TAG, "No such verse moving to prev chapter", nve);
		}
	}
	
	private void notifyObserversOfChange() {
		Log.d(TAG, "Notify current passage observers of change chapter:"+currentChapter);
		this.setChanged();
		this.notifyObservers();
	}
	
	public void setKey(Key key) {
		Verse verse = KeyUtil.getVerse(key);
		this.currentBibleBookNo = verse.getBook();
		this.currentChapter = verse.getChapter();
		setCurrentVerse(verse.getVerse());
		notifyObserversOfChange();
	}
	public void setKey(String keyText) {
		Log.d(TAG, "key text:"+keyText);
		try {
			Key key = currentDocument.getKey(keyText);
			setKey(key);
		} catch (NoSuchKeyException nske) {
			Log.e(TAG, "Invalid verse reference:"+keyText);
		}
	}
	
	public Key getKey() {
		Key key = null;
		try {
			String passageToShow = getCurrentBibleBook().getNormalizedShortName()+" ";
	
			// if only one chapter then don't add '1' or it is mistaken for verse
			if (!isSingleChapterBook()) {
				passageToShow += " "+currentChapter;
			}
	
			// if bible show whole chapter
			if (isSingleVerse()) {
				passageToShow += ":"+currentVerse.verse;
			}
	
			key = currentDocument.getKey(passageToShow);
		} catch (NoSuchKeyException nsve) {
			Log.e(TAG, "Bad verse "+key, nsve);
		}
		return key;
    }

	public Book getCurrentDocument() {
		return currentDocument;
	}

	public void setCurrentDocument(Book currentBible) {
		this.currentDocument = currentBible;
		notifyObserversOfChange();
	}

	public BookName getCurrentBibleBook() {
		try {
			return BibleInfo.getBookName(currentBibleBookNo);
		} catch (NoSuchVerseException nsve) {
			Log.e(TAG, "Error looking up BookName", nsve);
			//TODO need to improve error handling
			return null;
		}
	}

	public int getCurrentBibleBookNo() {
		return currentBibleBookNo;
	}

	public void setCurrentBibleBookNo(int currentBibleBookNo) {
		this.currentBibleBookNo = currentBibleBookNo;
	}

	public int getCurrentChapter() {
		return currentChapter;
	}
	
	public void setCurrentChapter(int currentChapter) {
		this.currentChapter = currentChapter;
		notifyObserversOfChange();
	}
	public boolean isSingleChapterBook() throws NoSuchKeyException{
    	return BibleInfo.chaptersInBook(currentBibleBookNo)==1;
	}
	
	public int getNumberOfVersesDisplayed() {
		int numVerses = 1;
		try {
			if (isSingleVerse()) {
				numVerses = 1;
			} else {
				numVerses = BibleInfo.versesInChapter(currentBibleBookNo, currentChapter);
			}
		} catch (NoSuchVerseException nsve) {
			Log.e(TAG, "Error finding number of verses", nsve);
		}
		return numVerses;
	}

	public boolean isSingleVerse() {
		return getCurrentDocument().getBookCategory().equals(BookCategory.COMMENTARY);
	}
	public int getCurrentVerse() {
		return currentVerse.verse;
	}
	public void setCurrentVerse(int verse) {
		currentVerse.setVerse(verse);
	}
	
	public void addVerseObserver(Observer observer) {
		currentVerse.addObserver(observer);
	}
	
	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	public void saveState(SharedPreferences outState) {
		SharedPreferences.Editor editor = outState.edit();
		editor.putString("document", CurrentPassage.getInstance().getCurrentDocument().getInitials());
		editor.putInt("bible-book", CurrentPassage.getInstance().getCurrentBibleBookNo());
		editor.putInt("chapter", CurrentPassage.getInstance().getCurrentChapter());
		editor.putInt("verse", CurrentPassage.getInstance().getCurrentVerse());
		editor.commit();
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
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
			currentBibleBookNo =  inState.getInt("bible-book", 1);
			currentChapter = inState.getInt("chapter", 1);
			currentVerse.verse = inState.getInt("verse", 1);

			Log.d(TAG, "Current passage:"+toString());
		} 
		// force an update here from default chapter/verse
		notifyObserversOfChange();
		currentVerse.notifyObservers();
	}
	
	class CurrentVerse extends Observable {
		private int verse = 1;
		
		private void setVerse(int verse) {
			this.verse = verse;
			this.setChanged();
			this.notifyObservers();
		}
	}
}
