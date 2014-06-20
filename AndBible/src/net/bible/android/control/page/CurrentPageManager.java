package net.bible.android.control.page;

import net.bible.android.BibleApplication;
import net.bible.android.SharedConstants;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.CurrentActivityHolder;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import de.greenrobot.event.EventBus;

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
	private CurrentMapPage currentMapPage;
	private CurrentMyNotePage currentMyNotePage;
	
	private CurrentPage currentDisplayedPage;
	
	private Screen splitScreenNo;
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
	// For split screen need 2 CurrentPageManagers
	private static CurrentPageManager screen1PageManager;
	private static CurrentPageManager screen2PageManager;
	
	// this was moved from the MainBibleActivity and has always been called this
	private static final String saveStateTag = "MainBibleActivity";

	private static final String TAG = "CurrentPageManager";
	
	static public CurrentPageManager getInstance() {
		if (splitScreenControl.getCurrentActiveScreen()==Screen.SCREEN_1) {
			return getInstance(Screen.SCREEN_1);
		} else {
			return getInstance(Screen.SCREEN_2);
		}
	}
	static public CurrentPageManager getInstance(Screen splitScreenNo) {
		if (screen1PageManager==null || screen2PageManager==null) {
			synchronized(CurrentPageManager.class)  {
				if (screen1PageManager==null) {
					screen1PageManager = new CurrentPageManager(Screen.SCREEN_1);
				}
				if (screen2PageManager==null) {
					screen2PageManager = new CurrentPageManager(Screen.SCREEN_2);
				}
			}
		}
		if (Screen.SCREEN_1 == splitScreenNo) {
			return screen1PageManager;
		} else {
			return screen2PageManager;
		}
	}

	private CurrentPageManager(Screen splitScreenNo) {
		this.splitScreenNo = splitScreenNo;
		
		currentBibleVerse = new CurrentBibleVerse();
		currentBiblePage = new CurrentBiblePage(currentBibleVerse);
		currentBiblePage.setBibleTraverser(ControlFactory.getInstance().getBibleTraverser());
		currentCommentaryPage = new CurrentCommentaryPage(currentBibleVerse);
		currentCommentaryPage.setBibleTraverser(ControlFactory.getInstance().getBibleTraverser());
		currentMyNotePage = new CurrentMyNotePage(currentBibleVerse);
		
		currentDictionaryPage = new CurrentDictionaryPage();
		currentGeneralBookPage = new CurrentGeneralBookPage();
		currentMapPage = new CurrentMapPage();
		
		currentDisplayedPage = currentBiblePage;
		
		// restore state from previous invocation
    	restoreState();

		// listen for AppToBackgroundEvent to save state when moved to background
    	EventBus.getDefault().register(this);
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
	public CurrentMapPage getCurrentMap() {
		return currentMapPage;
	}
	public CurrentMyNotePage getCurrentMyNotePage() {
		return currentMyNotePage;
	}

	/** 
	 * When navigating books and chapters there should always be a current Passage based book
	 */
	public AbstractPassageBook getCurrentPassageDocument() {
		return getCurrentVersePage().getCurrentPassageBook();
	}
	
	/** 
	 * Get current Passage based page or just return the Bible page
	 */
	public VersePage getCurrentVersePage() {
		VersePage page;
		if (isBibleShown() || isCommentaryShown()) {
			page = (VersePage)getCurrentPage();
		} else {
			page = getCurrentBible();
		}
		return page;
	}

	/** display a new Document and return the new Page
	 */
	public CurrentPage setCurrentDocument(Book nextDocument) {
		CurrentPage nextPage = null;
		if (nextDocument!=null) {
			PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
			
			nextPage = getBookPage(nextDocument);
	
			// is the next doc the same as the prev doc
			Book prevDocInPage = nextPage.getCurrentDocument();
			boolean sameDoc = nextDocument.equals(prevDocInPage);
			
			// must be in this order because History needs to grab the current doc before change
			nextPage.setCurrentDocument(nextDocument);
			currentDisplayedPage = nextPage;
			
			// page will change due to above
			// if there is a valid share key or the doc (hence the key) in the next page is the same then show the page straight away
			if (nextPage.getKey()!=null && (nextPage.isShareKeyBetweenDocs() || sameDoc || nextDocument.contains(nextPage.getKey()))) {
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
		return setCurrentDocumentAndKeyAndOffset(currentBook, key, SharedConstants.NO_VALUE);
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
	
	public CurrentPage getBookPage(Book book) {
		// book should never be null but it happened on one user's phone
		if (book==null) {
			return null;
		} else if (book.equals(currentMyNotePage.getCurrentDocument())) {
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
		} else if (bookCategory.equals(BookCategory.MAPS)) {
			bookPage = currentMapPage;
		} else if (bookCategory.equals(BookCategory.OTHER)) {
			bookPage = currentMyNotePage;
		}
		return bookPage;
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
	public boolean isMapShown() {
		return currentMapPage == currentDisplayedPage;
	}
	public void showBible() {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
		currentDisplayedPage = currentBiblePage;
		PassageChangeMediator.getInstance().onCurrentPageChanged();
	}

	/** 
	 * If app moves to background then save current state to allow continuation after return
	 * 
	 * @param appToBackgroundEvent Event info
	 */
	public void onEvent(AppToBackgroundEvent appToBackgroundEvent) {
		if (appToBackgroundEvent.isMovedToBackground()) {
			saveState();
		}
	}
    /** save current page and document state */
	protected void saveState() {
    	Log.i(TAG, "Save instance state for screen "+splitScreenNo);
    	SharedPreferences settings = getAppStateSharedPreferences();
		saveState(settings);
	}

	/** restore current page and document state */
    private void restoreState() {
    	try {
        	Log.i(TAG, "Restore instance state for screen "+splitScreenNo);
        	SharedPreferences settings = getAppStateSharedPreferences();
    		restoreState(settings);
    	} catch (Exception e) {
    		Log.e(TAG, "Restore error", e);
    	}
    }
	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	private void saveState(SharedPreferences outState) {
		Log.i(TAG, "save state");
		String screenId = getScreenIdForState();

		currentBiblePage.saveState(outState, screenId);
		currentCommentaryPage.saveState(outState, screenId);
		currentDictionaryPage.saveState(outState, screenId);
		currentGeneralBookPage.saveState(outState, screenId);
		currentMapPage.saveState(outState, screenId);
		
		SharedPreferences.Editor editor = outState.edit();
		editor.putString("currentPageCategory"+screenId, currentDisplayedPage.getBookCategory().getName());
		editor.commit();
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	private void restoreState(SharedPreferences inState) {
		Log.i(TAG, "restore state");
		String screenId = getScreenIdForState();

		currentBiblePage.restoreState(inState, screenId);
		currentCommentaryPage.restoreState(inState, screenId);
		currentDictionaryPage.restoreState(inState, screenId);
		currentGeneralBookPage.restoreState(inState, screenId);
		currentMapPage.restoreState(inState, screenId);
		
		String restoredPageCategoryName = inState.getString("currentPageCategory"+screenId, null);
		if (StringUtils.isNotEmpty(restoredPageCategoryName)) {
			BookCategory restoredBookCategory = BookCategory.fromString(restoredPageCategoryName);
			currentDisplayedPage = getBookPage(restoredBookCategory);
		}
	}
	
	private String getScreenIdForState() {
		// need to have empty screenId for screen 1 so as to use pre-splitScreen state
		return Screen.SCREEN_1==splitScreenNo? "" : "_SCREEN2";
	}
	
    private SharedPreferences getAppStateSharedPreferences() {
    	return BibleApplication.getApplication().getSharedPreferences(saveStateTag, 0);
    }
}
