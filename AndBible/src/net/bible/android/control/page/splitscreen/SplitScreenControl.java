package net.bible.android.control.page.splitscreen;

import java.util.List;

import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventManager;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.service.device.ScreenSettings;
import net.bible.service.format.Note;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

import android.util.Log;

public class SplitScreenControl {
	public enum Screen {SCREEN_1, SCREEN_2};
	
	private boolean isSplit = true;
	
	private boolean isSplitScreensLinked = true;
	
	private Screen currentActiveScreen = Screen.SCREEN_1;
	
	private Key lastActiveScreenKey;
	private boolean lastSynchWasInNightMode;
	private boolean isFirstSynchronize = true;
	
	private SplitScreenEventManager splitScreenEventManager = new SplitScreenEventManager();
	
	private static final String TAG = "SplitScreenControl";
	
	public boolean isFirstScreenActive() {
		return currentActiveScreen==Screen.SCREEN_1;
	}
	public boolean isCurrentActiveScreen(Screen currentActiveScreen) {
		return currentActiveScreen == this.currentActiveScreen;
	}
	
	/** Synchronise the inactive key and inactive screen with the active key and screen if required
	 */
	public void synchronizeScreens() {
		Log.d(TAG, "synchronizeScreens");
		CurrentPage activePage = CurrentPageManager.getInstance(getCurrentActiveScreen()).getCurrentPage();
		CurrentPage inactivePage = CurrentPageManager.getInstance(getNonActiveScreen()).getCurrentPage();
		Key activeScreenKey = activePage.getSingleKey();
		boolean isFirstTimeInit = (lastActiveScreenKey==null);
		boolean inactiveUpdated = false;
		boolean isTotalRefreshRequired = isFirstTimeInit ||	lastSynchWasInNightMode!=ScreenSettings.isNightMode();

		if (isSplit() && isSplitScreensLinked()) {

			// inactive screen may not be displayed but if switched to the key must be correct
			// Only Bible and cmtry are synch'd and they share a Verse key
			updateInactiveBibleKey();

			if (isSynchronizable(activePage) && isSynchronizable(inactivePage)) {
				// prevent infinite loop as each screen update causes a synchronize
				if (isFirstTimeInit || !lastActiveScreenKey.equals(activeScreenKey)) {
					updateInactiveScreen(inactivePage, activeScreenKey, inactivePage.getKey(), isTotalRefreshRequired);
					inactiveUpdated = true;
				}
			} 
		}

		// force inactive screen to display something otherwise it may be initially blank
		// or if nightMode has changed then force an update
		if (!inactiveUpdated && isTotalRefreshRequired) {
			// force an update of the inactive page to prevent blant screen
			updateInactiveScreen(inactivePage, inactivePage.getKey(), inactivePage.getKey(), isTotalRefreshRequired);
		}
		
		lastActiveScreenKey = activeScreenKey;
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
		
		// update split screen as smoothly as possible i.e. just scroll if verse is already on page
		if (!forceRefresh && BookCategory.BIBLE.equals(inactivePage.getCurrentDocument().getBookCategory()) && targetVerse.isSameChapter(currentVerse)	) {
			splitScreenEventManager.scrollSecondaryScreen(getNonActiveScreen(), targetVerse.getVerse());
		} else {
			new UpdateInactiveScreenTextTask().execute(inactivePage);
		}
		isFirstSynchronize = false;
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

		@Override
		protected void handleNotes(List<Note> notesList) {
			//NOOP
		}
    }

	public boolean isSplit() {
		return isSplit;
	}
	public void setSplit(boolean isSplit) {
		if (this.isSplit!=isSplit) {
			this.isSplit = isSplit;
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
