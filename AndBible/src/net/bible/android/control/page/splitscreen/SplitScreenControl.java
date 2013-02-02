package net.bible.android.control.page.splitscreen;

import java.util.List;

import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventManager;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.service.format.Note;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

public class SplitScreenControl {
	public enum Screen {SCREEN_1, SCREEN_2};
	
	private boolean isSplit = true;
	
	private boolean isSplitScreensLinked = true;
	
	private Screen currentActiveScreen = Screen.SCREEN_1;
	
	private Key lastActiveScreenKey;
	private boolean isFirstSynchronize = true;
	
	private SplitScreenEventManager splitScreenEventManager = new SplitScreenEventManager();
	
	private static final String TAG = "SplitScreenControl";
	
	public boolean isFirstScreenActive() {
		return currentActiveScreen==Screen.SCREEN_1;
	}
	public boolean isCurrentActiveScreen(Screen currentActiveScreen) {
		return currentActiveScreen == this.currentActiveScreen;
	}
	
	public void synchronizeScreens() {
		CurrentPage activePage = CurrentPageManager.getInstance(getCurrentActiveScreen()).getCurrentPage();
		CurrentPage inactivePage = CurrentPageManager.getInstance(getNonActiveScreen()).getCurrentPage();
		Key activeScreenKey = activePage.getSingleKey();
		boolean isFirstTimeInit = (lastActiveScreenKey==null);
		boolean inactiveUpdated = false;
		
		if (isSplit() && isSplitScreensLinked()) {
			
			if (isSynchronizable(activePage) && isSynchronizable(inactivePage)) {
				// prevent infinite loop as each screen update causes a synchronize
				if (isFirstTimeInit || !lastActiveScreenKey.equals(activeScreenKey)) {
					updateInactiveScreen(inactivePage, activeScreenKey, inactivePage.getKey());
					inactiveUpdated = true;
				}
			} 
		}

		// force inactive screen to display something otherwise it may be initially blank
		if (isFirstTimeInit && !inactiveUpdated) {
			// force an update of the inactive page to prevent blant screen
			updateInactiveScreen(inactivePage, inactivePage.getKey(), inactivePage.getKey());
		}
		
		lastActiveScreenKey = activeScreenKey;
	}
	
	private void updateInactiveScreen(CurrentPage inactivePage,	Key targetScreenKey, Key inactiveScreenKey) {
		// only bibles and commentaries get this far so fine to convert key to verse
		Verse targetVerse = KeyUtil.getVerse(targetScreenKey);
		Verse currentVerse = KeyUtil.getVerse(inactiveScreenKey);
		
		// update secondary split CurrentPage info using doSetKey which does not cause screen updates
		inactivePage.doSetKey(targetScreenKey);
		
		// update split screen as smoothly as possible i.e. just scroll if verse is already on page
		if (!isFirstSynchronize && BookCategory.BIBLE.equals(inactivePage.getCurrentDocument().getBookCategory()) && targetVerse.isSameChapter(currentVerse)	) {
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
			synchronizeScreens();
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
