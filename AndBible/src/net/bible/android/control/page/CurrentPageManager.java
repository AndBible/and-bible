package net.bible.android.control.page;

import net.bible.android.control.PassageChangeMediator;
import net.bible.android.view.activity.base.CurrentActivityHolder;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class CurrentPageManager {
	// use the same verse in the commentary and bible to keep them in sync
	private CurrentBibleVerse currentBibleVerse;
	private CurrentBiblePage currentBiblePage;
	private CurrentCommentaryPage currentCommentaryPage;
	private CurrentDictionaryPage currentDictionaryPage;
	private CurrentGeneralBookPage currentGeneralBookPage;
	
	private CurrentPage currentDisplayedPage;
	
	private static CurrentPageManager singleton;
	
	private static final String TAG = "CurrentPageManager";
	
	static public CurrentPageManager getInstance() {
		if (singleton==null) {
			synchronized(CurrentPageManager.class)  {
				if (singleton==null) {
					CurrentPageManager instance = new CurrentPageManager();
					instance.currentBibleVerse = new CurrentBibleVerse();
					instance.currentBiblePage = new CurrentBiblePage(instance.currentBibleVerse);
					instance.currentCommentaryPage = new CurrentCommentaryPage(instance.currentBibleVerse);
					
					instance.currentDictionaryPage = new CurrentDictionaryPage();
					instance.currentGeneralBookPage = new CurrentGeneralBookPage();
					
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
	public CurrentCommentaryPage getCurrentCommentary() {
		return currentCommentaryPage;
	}
	public CurrentDictionaryPage getCurrentDictionary() {
		return currentDictionaryPage;
	}
	public CurrentGeneralBookPage getCurrentGeneralBook() {
		return currentGeneralBookPage;
	}
	
	public CurrentPage setCurrentDocument(Book currentBook) {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();

		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			// must be in this order because History needs to grab the current doc before change
			nextPage.setCurrentDocument(currentBook);
			currentDisplayedPage = nextPage;
		}
		
		// page will change due to above
		// if there is a valid share key then show the page straight away
		if (nextPage.isShareKeyBetweenDocs()) {
			PassageChangeMediator.getInstance().onCurrentPageChanged();
		} else {
			Context context = CurrentActivityHolder.getInstance().getCurrentActivity();
			// pop up a key selection screen
	    	Intent intent = new Intent(context, nextPage.getKeyChooserActivity());
	    	context.startActivity(intent);
		}

		return nextPage;
	}

	public CurrentPage setCurrentDocumentAndKey(Book currentBook, Key key) {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();

		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			try {
				nextPage.setInhibitChangeNotifications(true);
				nextPage.setCurrentDocument(currentBook);
				nextPage.setKey(key);
				currentDisplayedPage = nextPage;
			} finally {
				nextPage.setInhibitChangeNotifications(false);
			}
		}
		// valid key has been set so do not need to show a key chooser therefore just update main view
		PassageChangeMediator.getInstance().onCurrentPageChanged();

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
		} else if (bookCategory.equals(BookCategory.GENERAL_BOOK)) {
			bookPage = currentGeneralBookPage;
		}
		return bookPage;
	}

	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	public void saveState(SharedPreferences outState) {
		currentBiblePage.saveState(outState);
		currentCommentaryPage.saveState(outState);
		currentDictionaryPage.saveState(outState);
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	public void restoreState(SharedPreferences inState) {
		currentBiblePage.restoreState(inState);
		currentCommentaryPage.restoreState(inState);
		currentDictionaryPage.restoreState(inState);
	}
	
	public boolean isCommentaryShown() {
		return currentCommentaryPage == currentDisplayedPage;
	}
	public boolean isBibleShown() {
		return currentBiblePage == currentDisplayedPage;
	}
	public void showBible() {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
		currentDisplayedPage = currentBiblePage;
		PassageChangeMediator.getInstance().onCurrentPageChanged();
	}
}
