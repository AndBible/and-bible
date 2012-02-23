package net.bible.android.control.page;

import net.bible.android.BibleApplication;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.view.activity.base.CurrentActivityHolder;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/** Control singletons of the different current document page types
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentPageManager {
	// use the same verse in the commentary and bible to keep them in sync
	private CurrentBibleVerse currentBibleVerse;
	private CurrentBiblePage currentBiblePage;
	private CurrentCommentaryPage currentCommentaryPage;
	private CurrentDictionaryPage currentDictionaryPage;
	private CurrentGeneralBookPage currentGeneralBookPage;
	private CurrentMyNotePage currentMyNotePage;
	
	private CurrentPage currentDisplayedPage;
	
	private static CurrentPageManager singleton;
	
	// this was moved from the MainBibleActivity and has always been called this
	private static final String saveStateTag = "MainBibleActivity";

	private static final String TAG = "CurrentPageManager";
	
	static public CurrentPageManager getInstance() {
		if (singleton==null) {
			synchronized(CurrentPageManager.class)  {
				if (singleton==null) {
					CurrentPageManager instance = new CurrentPageManager();
					singleton = instance;
				}
			}
		}
		return singleton;
	}

	private CurrentPageManager() {
		currentBibleVerse = new CurrentBibleVerse();
		currentBiblePage = new CurrentBiblePage(currentBibleVerse);
		currentCommentaryPage = new CurrentCommentaryPage(currentBibleVerse);
		currentMyNotePage = new CurrentMyNotePage(currentBibleVerse);
		
		currentDictionaryPage = new CurrentDictionaryPage();
		currentGeneralBookPage = new CurrentGeneralBookPage();
		
		currentDisplayedPage = currentBiblePage;
		
		// restore state from previous invocation
    	restoreState();
    	
		// register to save state when moved to background
    	CurrentActivityHolder.getInstance().addAppToBackgroundListener(new AppToBackgroundListener() {
			@Override
			public void applicationNowInBackground(AppToBackgroundEvent e) {
				saveState();
			}
		});
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
	public CurrentMyNotePage getCurrentMyNotePage() {
		return currentMyNotePage;
	}

	/** display a new Document and return the new Page
	 */
	public CurrentPage setCurrentDocument(Book nextDocument) {
		CurrentPage nextPage = null;
		if (nextDocument!=null) {
			PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
			
			nextPage = getBookPage(nextDocument);
	
			// is the next doc the same as the prev doc
			boolean sameDoc = nextDocument.equals(nextPage.getCurrentDocument());
			
			// must be in this order because History needs to grab the current doc before change
			nextPage.setCurrentDocument(nextDocument);
			currentDisplayedPage = nextPage;
			
			// page will change due to above
			// if there is a valid share key or the doc (hence the key) in the next page is the same then show the page straight away
			if ((nextPage.isShareKeyBetweenDocs() || sameDoc) && nextPage.getKey()!=null) {
				PassageChangeMediator.getInstance().onCurrentPageChanged();
			} else {
				Context context = CurrentActivityHolder.getInstance().getCurrentActivity();
				// pop up a key selection screen
		    	Intent intent = new Intent(context, nextPage.getKeyChooserActivity());
		    	context.startActivity(intent);
			}
		} else {
			// should never get here because a doc should always be passed in but I have seen errors lie this once or twice
			nextPage = currentDisplayedPage;
		}
	
		return nextPage;
	}

	/** My Note is different to all other pages.  It has no documents etc but I attempt to make it look a bit like a Commentary page
	 * 
	 * @param showing
	 */
	public void showMyNote() {
		showMyNote(currentMyNotePage.getKey());
	}
	
	public void showMyNote(Key verse) {
		setCurrentDocumentAndKey(currentMyNotePage.getCurrentDocument(), verse);
	}

	public CurrentPage setCurrentDocumentAndKey(Book currentBook, Key key) {
		return setCurrentDocumentAndKeyAndOffset(currentBook, key, 0);
	}
	public CurrentPage setCurrentDocumentAndKeyAndOffset(Book currentBook, Key key, float yOffsetRatio) {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();

		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			try {
				nextPage.setInhibitChangeNotifications(true);
				nextPage.setCurrentDocument(currentBook);
				nextPage.setKey(key);
				nextPage.setCurrentYOffsetRatio(yOffsetRatio);
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
		if (book.equals(currentMyNotePage.getCurrentDocument())) {
			return currentMyNotePage;
		} else {
			return getBookPage(book.getBookCategory());
		}
		
	}		
	private CurrentPage getBookPage(BookCategory bookCategory) {

		CurrentPage bookPage = null;
		if (bookCategory.equals(BookCategory.BIBLE)) {
			bookPage = currentBiblePage;
		} else if (bookCategory.equals(BookCategory.COMMENTARY)) {
			bookPage = currentCommentaryPage;
		} else if (bookCategory.equals(BookCategory.DICTIONARY)) {
			bookPage = currentDictionaryPage;
		} else if (bookCategory.equals(BookCategory.GENERAL_BOOK)) {
			bookPage = currentGeneralBookPage;
		} else if (bookCategory.equals(BookCategory.OTHER)) {
			bookPage = currentMyNotePage;
		}
		return bookPage;
	}

	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	public void saveState(SharedPreferences outState) {
		Log.i(TAG, "save state");
		currentBiblePage.saveState(outState);
		currentCommentaryPage.saveState(outState);
		currentDictionaryPage.saveState(outState);
		currentGeneralBookPage.saveState(outState);
		
		SharedPreferences.Editor editor = outState.edit();
		editor.putString("currentPageCategory", currentDisplayedPage.getBookCategory().getName());
		editor.commit();
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	public void restoreState(SharedPreferences inState) {
		Log.i(TAG, "restore state");
		currentBiblePage.restoreState(inState);
		currentCommentaryPage.restoreState(inState);
		currentDictionaryPage.restoreState(inState);
		currentGeneralBookPage.restoreState(inState);
		
		String restoredPageCategoryName = inState.getString("currentPageCategory", null);
		if (StringUtils.isNotEmpty(restoredPageCategoryName)) {
			BookCategory restoredBookCategory = BookCategory.fromString(restoredPageCategoryName);
			currentDisplayedPage = getBookPage(restoredBookCategory);
		}
	}
	
	public boolean isCommentaryShown() {
		return currentCommentaryPage == currentDisplayedPage;
	}
	public boolean isBibleShown() {
		return currentBiblePage == currentDisplayedPage;
	}
	public boolean isDictionaryShown() {
		return currentDictionaryPage == currentDisplayedPage;
	}
	public boolean isGenBookShown() {
		return currentGeneralBookPage == currentDisplayedPage;
	}
	public boolean isMyNoteShown() {
		return currentMyNotePage == currentDisplayedPage;
	}
	public void showBible() {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
		currentDisplayedPage = currentBiblePage;
		PassageChangeMediator.getInstance().onCurrentPageChanged();
	}

    /** save current page and document state */
	protected void saveState() {
    	Log.i(TAG, "Saving instance state");
    	SharedPreferences settings = getAppStateSharedPreferences();
		saveState(settings);
	}

	/** restore current page and document state */
    private void restoreState() {
    	try {
        	Log.i(TAG, "Restore instance state");
        	SharedPreferences settings = getAppStateSharedPreferences();
    		restoreState(settings);
    	} catch (Exception e) {
    		Log.e(TAG, "Restore error", e);
    	}
    }
    
    private SharedPreferences getAppStateSharedPreferences() {
    	return BibleApplication.getApplication().getSharedPreferences(saveStateTag, 0);
    }
}
