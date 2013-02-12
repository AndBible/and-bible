package net.bible.android.control.page.splitscreen;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventManager;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.device.ScreenSettings;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

import android.util.Log;

public class SplitScreenControl {
	public enum Screen {SCREEN_1, SCREEN_2};
	
	private boolean isSplit = true;
	private boolean isScreen2Minimized;
	
	private boolean isSplitScreensLinked = true;
	
	private Screen currentActiveScreen = Screen.SCREEN_1;
	
	private Key lastSynchdInactiveScreenKey;
	private boolean lastSynchWasInNightMode;
	
	private SplitScreenEventManager splitScreenEventManager = new SplitScreenEventManager();
	
	private static final String TAG = "SplitScreenControl";
	
	public SplitScreenControl() {
		CurrentActivityHolder.getInstance().addAppToBackgroundListener(new AppToBackgroundListener() {
			@Override
			public void applicationNowInBackground(AppToBackgroundEvent e) {
				// ensure nonactive screen is initialised when returning from background
				lastSynchdInactiveScreenKey = null;
			}

			@Override
			public void applicationReturnedFromBackground(AppToBackgroundEvent e) {
				lastSynchdInactiveScreenKey = null;
			}
		});
	}
	
	public boolean isFirstScreenActive() {
		return currentActiveScreen==Screen.SCREEN_1;
	}
	public boolean isCurrentActiveScreen(Screen currentActiveScreen) {
		return currentActiveScreen == this.currentActiveScreen;
	}
	
	public void minimiseScreen2() {
		setSplit(false);
		isScreen2Minimized = true;
		// calling sCAS will cause events to be dispatched to set active screen so auto-scroll works
		setCurrentActiveScreen(Screen.SCREEN_1);
		// redisplay the current page
		splitScreenEventManager.numberOfScreensChanged();
	}

	public void restoreScreen2() {
		isScreen2Minimized = false;
		currentActiveScreen = Screen.SCREEN_1;
		isSplit = true;
		// causes BibleViews to be created and laid out
		splitScreenEventManager.numberOfScreensChanged();
		synchronizeScreens();
	}
	
	/** Synchronise the inactive key and inactive screen with the active key and screen if required
	 */
	public void synchronizeScreens() {
		Log.d(TAG, "synchronizeScreens active:"+currentActiveScreen);
		CurrentPage activePage = CurrentPageManager.getInstance(getCurrentActiveScreen()).getCurrentPage();
		CurrentPage inactivePage = CurrentPageManager.getInstance(getNonActiveScreen()).getCurrentPage();
		Key activeScreenKey = activePage.getSingleKey();
		Log.d(TAG, "active key:"+activeScreenKey);
		Key inactiveScreenKey = inactivePage.getSingleKey();
		boolean isFirstTimeInit = (lastSynchdInactiveScreenKey==null);
		boolean inactiveUpdated = false;
		boolean isTotalRefreshRequired = isFirstTimeInit ||	lastSynchWasInNightMode!=ScreenSettings.isNightMode();

		if (isSplit() && isSplitScreensLinked()) {

			// inactive screen may not be displayed but if switched to the key must be correct
			// Only Bible and cmtry are synch'd and they share a Verse key
			updateInactiveBibleKey();

			// prevent infinite loop as each screen update causes a synchronise by comparing last key
			// only update pages if empty or synchronised
			if (isFirstTimeInit || 
			   (isSynchronizable(activePage) && isSynchronizable(inactivePage) && !lastSynchdInactiveScreenKey.equals(activeScreenKey)) ) {
				updateInactiveScreen(inactivePage, activeScreenKey, inactiveScreenKey, isTotalRefreshRequired);
				lastSynchdInactiveScreenKey = activeScreenKey;
				inactiveUpdated = true;
			} 
		}

		// force inactive screen to display something otherwise it may be initially blank
		// or if nightMode has changed then force an update
		if (!inactiveUpdated && isTotalRefreshRequired) {
			// force an update of the inactive page to prevent blank screen
			updateInactiveScreen(inactivePage, inactiveScreenKey, inactiveScreenKey, isTotalRefreshRequired);
		}
		
		lastSynchWasInNightMode = ScreenSettings.isNightMode();
	}
	
	/** Only call if screens are synchronised.  Update synch'd keys even if inactive page not shown so if it is shown then it is correct
	 */
	private void updateInactiveBibleKey() {
		Log.d(TAG, "updateInactiveBibleKey");
		// update secondary split CurrentPage info using doSetKey which does not cause screen updates
		Key activeBibleKey = CurrentPageManager.getInstance(getCurrentActiveScreen()).getCurrentBible().getSingleKey();
		CurrentPageManager.getInstance(getNonActiveScreen()).getCurrentBible().doSetKey(activeBibleKey);
	}
	
	/** refresh/synch inactive screen if required
	 */
	private void updateInactiveScreen(CurrentPage inactivePage,	Key targetScreenKey, Key inactiveScreenKey, boolean forceRefresh) {
		Log.d(TAG, "updateInactiveScreen");
		// only bibles and commentaries get this far so fine to convert key to verse
		Verse targetVerse = KeyUtil.getVerse(targetScreenKey);
		Verse currentVerse = KeyUtil.getVerse(inactiveScreenKey);
		
		// update split screen as smoothly as possible i.e. just scroll if verse is adjacent on current page
		if (!forceRefresh && BookCategory.BIBLE.equals(inactivePage.getCurrentDocument().getBookCategory()) && 
				targetVerse.isSameChapter(currentVerse)  && targetVerse.adjacentTo(currentVerse)) {
			splitScreenEventManager.scrollSecondaryScreen(getNonActiveScreen(), targetVerse.getVerse());
		} else {
			new UpdateInactiveScreenTextTask().execute(inactivePage);
		}
	}

	/** only Bibles and commentaries have the same sort of key and can be synchronized
	 */
	private boolean isSynchronizable(CurrentPage page) {
		return BookCategory.BIBLE.equals(page.getCurrentDocument().getBookCategory()) || BookCategory.COMMENTARY.equals(page.getCurrentDocument().getBookCategory()); 
	}

    private class UpdateInactiveScreenTextTask extends UpdateTextTask {

        /** callback from base class when result is ready */
    	@Override
    	protected void showText(String text, int verseNo, float yOffsetRatio) {
    		splitScreenEventManager.updateSecondaryScreen(getNonActiveScreen(), text, verseNo);
        }
    }

	public boolean isSplit() {
		return isSplit;
	}
	public void setSplit(boolean isSplit) {
		if (this.isSplit!=isSplit) {
			this.isSplit = isSplit;
			// if split is false or true then it can no longer be minimised
			isScreen2Minimized = false;
			if (isSplit) {
				synchronizeScreens();
			}
		}
	}

	public Screen getNonActiveScreen() {
		return currentActiveScreen==Screen.SCREEN_1? Screen.SCREEN_2 : Screen.SCREEN_1;
	}
	public Screen getCurrentActiveScreen() {
		return currentActiveScreen;
	}
	public void setCurrentActiveScreen(Screen currentActiveScreen) {
		if (currentActiveScreen != this.currentActiveScreen) {
			this.currentActiveScreen = currentActiveScreen;
			splitScreenEventManager.splitScreenDetailChanged(this.currentActiveScreen);
		}
	}

	public boolean isSplitScreensLinked() {
		return isSplitScreensLinked;
	}
	public void setSplitScreensLinked(boolean isSplitScreensLinked) {
		this.isSplitScreensLinked = isSplitScreensLinked;
	}
	
	public boolean isScreen2Minimized() {
		return isScreen2Minimized;
	}

	// Event listener management code
	public void addSplitScreenEventListener(SplitScreenEventListener listener) 
	{
	     splitScreenEventManager.addSplitScreenEventListener(listener);
	}
	public void removeSplitScreenEventListener(SplitScreenEventListener listener) 
	{
		splitScreenEventManager.removeSplitScreenEventListener(listener);
	}
}
