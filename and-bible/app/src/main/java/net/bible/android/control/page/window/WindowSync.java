package net.bible.android.control.page.window;

import java.util.List;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.passage.PassageChangedEvent;
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent;
import net.bible.android.control.event.window.UpdateSecondaryWindowEvent;
import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.service.device.ScreenSettings;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

import de.greenrobot.event.EventBus;

public class WindowSync {

	private boolean isFirstTimeInit = true;
	private boolean resynchRequired = false;
	private boolean screenPreferencesChanged = false;
	
	private Key lastSynchdInactiveWindowKey;
	private boolean lastSynchWasInNightMode;
	
	private WindowRepository windowRepository;
	
	public WindowSync(WindowRepository windowRepository) {
		this.windowRepository = windowRepository;
		
		// register for passage change and fore/background events
		EventBus.getDefault().register(this);
	}

	public void onEvent(PassageChangedEvent event) {
    	synchronizeScreens();
    }
	
	/**
	 * Save/restore dynamic state that is not automatically saved as Preferences
	 */
	public void onEvent(AppToBackgroundEvent event) {
		if (event.isMovedToBackground()) {
			// ensure nonactive screen is initialised when returning from background
			lastSynchdInactiveWindowKey = null;
		} else {
			lastSynchdInactiveWindowKey = null;
		}
	}

	/** Synchronise the inactive key and inactive screen with the active key and screen if required
	 */
	public void synchronizeScreens() {
		Window activeWindow = windowRepository.getActiveWindow();
		CurrentPage activePage = activeWindow.getPageManager().getCurrentPage();
		Key targetActiveWindowKey = activePage.getSingleKey();

		List<Window> inactiveWindowList = windowRepository.getWindowsToSynchronise();
		for (Window inactiveWindow : inactiveWindowList) {
			CurrentPage inactivePage = inactiveWindow.getPageManager().getCurrentPage();
			Key inactiveWindowKey = inactivePage.getSingleKey();
			boolean inactiveUpdated = false;
			boolean isTotalRefreshRequired = isFirstTimeInit ||	lastSynchWasInNightMode!=ScreenSettings.isNightMode() || screenPreferencesChanged || resynchRequired;
	
			if (isSynchronizableVerseKey(activePage) && activeWindow.isSynchronised() && inactiveWindow.isSynchronised()) {
				// inactive screen may not be displayed (e.g. if viewing a dict) but if switched to the key must be correct
				// Only Bible and cmtry are synch'd and they share a Verse key
				updateInactiveBibleKey(inactiveWindow, targetActiveWindowKey);
				
				if (isSynchronizableVerseKey(inactivePage) && inactiveWindow.isVisible()) {
					// re-get as it may have been mapped to the correct v11n
					// this looks odd but the inactivePage key has already been updated to the activeScreenKey 
					targetActiveWindowKey = inactivePage.getSingleKey();
					
					// prevent infinite loop as each screen update causes a synchronise by comparing last key
					// only update pages if empty or synchronised
					if (isFirstTimeInit || resynchRequired || 
					   (!targetActiveWindowKey.equals(lastSynchdInactiveWindowKey)) ) {
						updateInactiveWindow(inactiveWindow, inactivePage, targetActiveWindowKey, lastSynchdInactiveWindowKey, isTotalRefreshRequired);
						inactiveUpdated = true;
					} 
				}
			}
				
			// force inactive screen to display something otherwise it may be initially blank
			// or if nightMode has changed then force an update
			if (!inactiveUpdated && isTotalRefreshRequired) {
				// force an update of the inactive page to prevent blank screen
				updateInactiveWindow(inactiveWindow, inactivePage, inactiveWindowKey, inactiveWindowKey, isTotalRefreshRequired);
				inactiveUpdated = true;
			}
			
		}
		lastSynchdInactiveWindowKey = targetActiveWindowKey;
		lastSynchWasInNightMode = ScreenSettings.isNightMode();
		screenPreferencesChanged = false;
		resynchRequired = false;
		isFirstTimeInit = false;
	}
	
	/** Only call if screens are synchronised.  Update synch'd keys even if inactive page not shown so if it is shown then it is correct
	 */
	private void updateInactiveBibleKey(Window inactiveWindow, Key activeWindowKey) {
		inactiveWindow.getPageManager().getCurrentBible().doSetKey(activeWindowKey);
	}
	
	/** refresh/synch inactive screen if required
	 */
	private void updateInactiveWindow(Window inactiveWindow, CurrentPage inactivePage,	Key targetKey, Key inactiveWindowKey, boolean forceRefresh) {
		// standard null checks
		if (targetKey!=null && inactivePage!=null) {
			// Not just bibles and commentaries get this far so NOT always fine to convert key to verse
			Verse targetVerse = null;
			Versification targetV11n = null;
			if (targetKey instanceof Verse) {
				targetVerse = KeyUtil.getVerse(targetKey);
				targetV11n = targetVerse.getVersification();
			}
			
			Verse currentVerse = null;
			boolean isGeneralBook = BookCategory.GENERAL_BOOK.equals(inactivePage.getCurrentDocument().getBookCategory());
			boolean isUnsynchronizedCommentary = !inactiveWindow.isSynchronised()
					&& BookCategory.COMMENTARY.equals(inactivePage.getCurrentDocument().getBookCategory());

			if (inactiveWindowKey!=null && inactiveWindowKey instanceof Verse) {
				currentVerse = KeyUtil.getVerse(inactiveWindowKey);
			}
			
			// update inactive screens as smoothly as possible i.e. just jump/scroll if verse is on current page
			if (!forceRefresh && 
					BookCategory.BIBLE.equals(inactivePage.getCurrentDocument().getBookCategory()) && 
					currentVerse!=null && targetVerse!=null && targetV11n.isSameChapter(targetVerse, currentVerse)) {
				EventBus.getDefault().post(new ScrollSecondaryWindowEvent(inactiveWindow, ChapterVerse.fromVerse(targetVerse)));
			} else if(isGeneralBook || isUnsynchronizedCommentary)
			{
				// Do not update! Updating would reset page position.
			} else {
				new UpdateInactiveScreenTextTask().execute(inactiveWindow);
			}
		}
	}

	/** Only Bibles and commentaries have the same sort of key and can be synchronized
	 */
	private boolean isSynchronizableVerseKey(CurrentPage page) {
		boolean result = false;
		// various null checks then the test
		if (page!=null) {
			Book document = page.getCurrentDocument();
			if (document!=null) {
				BookCategory bookCategory = document.getBookCategory();
				// The important part
				result = BookCategory.BIBLE.equals(bookCategory) || BookCategory.COMMENTARY.equals(bookCategory); 
			}
		}
		return result; 
	}

    private class UpdateInactiveScreenTextTask extends UpdateTextTask {
        /** callback from base class when result is ready */
    	@Override
    	protected void showText(String text, Window window, ChapterVerse chapterVerse, float yOffsetRatio) {
    		EventBus.getDefault().post(new UpdateSecondaryWindowEvent(window, text, chapterVerse));
        }
    }

	public void setResynchRequired(boolean resynchRequired) {
		this.resynchRequired = resynchRequired;
	}

	public void setScreenPreferencesChanged(boolean screenPreferencesChanged) {
		this.screenPreferencesChanged = screenPreferencesChanged;
	}
}
