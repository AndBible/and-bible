package net.bible.android.control.page;

import net.bible.android.control.PassageChangeMediator;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;

public class CurrentPageManager {
	// use the same verse in the commentary and bible to keep them in sync
	private CurrentBibleVerse currentBibleVerse;
	private CurrentBiblePage currentBiblePage;
	private CurrentCommentaryPage currentCommentaryPage;
	private CurrentDictionaryPage currentDictionaryPage;
	
	private CurrentPage currentDisplayedPage;
	
	private static CurrentPageManager singleton;
	
	static public CurrentPageManager getInstance() {
		if (singleton==null) {
			synchronized(CurrentPageManager.class)  {
				if (singleton==null) {
					CurrentPageManager instance = new CurrentPageManager();
					instance.currentBibleVerse = new CurrentBibleVerse();
					instance.currentBiblePage = new CurrentBiblePage(instance.currentBibleVerse);
					instance.currentCommentaryPage = new CurrentCommentaryPage(instance.currentBibleVerse);
					
					instance.currentDictionaryPage = new CurrentDictionaryPage();
					
					instance.currentDisplayedPage = instance.currentBiblePage;
					singleton = instance;
				}
			}
		}
		return singleton;
	}
	
	public CurrentPage getCurrentPage() {
		return currentDisplayedPage;
	}
	public CurrentBiblePage getCurrentBible() {
		return currentBiblePage;
	}
	public CurrentDictionaryPage getCurrentDictionary() {
		return currentDictionaryPage;
	}
	
	public CurrentPage setCurrentDocument(Book currentBook) {
		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			currentDisplayedPage = nextPage;
			nextPage.setCurrentDocument(currentBook);
		}
		
		return nextPage;
	}

	public CurrentPage setCurrentDocumentAndKey(Book currentBook, Key key) {
		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			try {
				// don't update screen until key is set
				nextPage.setInhibitChangeNotifications(true);
				currentDisplayedPage = nextPage;
				nextPage.setCurrentDocument(currentBook);
			} finally {
				nextPage.setInhibitChangeNotifications(false);
			}
		}

		nextPage.setKey(key);
		return nextPage;
	}
	
	private CurrentPage getBookPage(Book book) {
		BookCategory bookCategory = book.getBookCategory();
		CurrentPage bookPage = null;
		if (bookCategory.equals(BookCategory.BIBLE)) {
			bookPage = currentBiblePage;
		} else if (bookCategory.equals(BookCategory.COMMENTARY)) {
			bookPage = currentCommentaryPage;
		} else if (bookCategory.equals(BookCategory.DICTIONARY)) {
			bookPage = currentDictionaryPage;
		}
		return bookPage;
	}

	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	public void saveState(SharedPreferences outState) {
		//xxtodo save & restore other module types
		currentBiblePage.saveState(outState);
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	public void restoreState(SharedPreferences inState) {
		//xxtodo save & restore other module types
		currentBiblePage.restoreState(inState);
	}
	
	public boolean isBibleShown() {
		return currentBiblePage == currentDisplayedPage;
	}
	public void showBible() {
		currentDisplayedPage = currentBiblePage;
		PassageChangeMediator.getInstance().onCurrentPageChanged();
	}
}
