package net.bible.android.control.page.splitscreen;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.passage.PassageChangedEvent;
import net.bible.android.control.event.splitscreen.CurrentSplitScreenChangedEvent;
import net.bible.android.control.event.splitscreen.NumberOfScreensChangedEvent;
import net.bible.android.control.event.splitscreen.ScrollSecondaryScreenEvent;
import net.bible.android.control.event.splitscreen.SplitScreenSizeChangedEvent;
import net.bible.android.control.event.splitscreen.UpdateSecondaryScreenEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.android.control.page.splitscreen.Screen.ScreenState;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;
import de.greenrobot.event.EventBus;

/**
 * Central control of Split screens especially synchronization
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SplitScreenControl {

	//TODO remove both
	private boolean isSplit;
	private boolean isSplitScreensLinked;
	
	private boolean isSeparatorMoving = false;
	private long stoppedMovingTime = 0;
	private float screen1Weight = 0.5f;

	private boolean resynchRequired = false;
	private boolean screenPreferencesChanged = false;
	
	private ScreenManager screenManager = new ScreenManager();
	
	private Key lastSynchdInactiveScreenKey;
	private boolean lastSynchWasInNightMode;
	
	public static int SCREEN_SETTLE_TIME_MILLIS = 1000;
	
	public static final String SPLIT_SCREEN_PREF = "split_screen_pref";
	private static final String PREFS_SPLIT_SCREEN_SINGLE = "single";
	private static final String PREFS_SPLIT_SCREEN_LINKED = "linked";
	private static final String PREFS_SPLIT_SCREEN_NOT_LINKED = "not_linked";
	private static final String SPLIT_SCREEN1_WEIGHT = "screen1_weight";
	private static final String SPLIT_SCREEN2_MINIMIZED = "screen2_minimized";
	
	private static final String TAG = "SplitScreenControl";
	
	private OnSharedPreferenceChangeListener onSettingsChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,	String key) {
			if (SPLIT_SCREEN_PREF.equals(key)) {
				restoreFromSettings();
			} else {
				Log.d(TAG, "screen preferences changed so inactive screen needs to be refreshed");
				screenPreferencesChanged = true;
			}
			
		}
	};
	
	public SplitScreenControl() {
		restoreNonPreferenceState();
		restoreFromSettings();
		
		// the listener needs to be a class variable because it is held in a WeakHashMap by SharedPreferences
		CommonUtils.getSharedPreferences().registerOnSharedPreferenceChangeListener(onSettingsChangeListener);
		
		// register for passage change and fore/background events
		EventBus.getDefault().register(this);
	}

	public Screen getScreen(int screenNo) {
		return screenManager.getScreen(screenNo);
	}
	public Screen getActiveScreen() {
		return screenManager.getCurrentActiveScreen();
	}
	public boolean isCurrentActiveScreen(Screen currentActiveScreen) {
		return currentActiveScreen == screenManager.getCurrentActiveScreen();
	}
	
	public void minimiseScreen(Screen screen) {
		//TODO may have to maximise another screen if there is only 1 screen unminimised
		setSplit(false);
		
		screen.setState(ScreenState.MINIMISED);
		// calling sCAS will cause events to be dispatched to set active screen so auto-scroll works
		screenManager.setDefaultActiveScreen();
		// redisplay the current page
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
	}

	public void restoreScreen(Screen screen) {
		screen.setState(ScreenState.SPLIT);
		screenManager.setDefaultActiveScreen();
		isSplit = true;
		// causes BibleViews to be created and laid out
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
		
		synchronizeScreens();
	}

	/** screen orientation has changed */
	public void orientationChange() {
		// causes BibleViews to be created and laid out
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
	}
	
    public void onEvent(PassageChangedEvent event) {
    	synchronizeScreens();
    }
	
	/** Synchronise the inactive key and inactive screen with the active key and screen if required
	 */
	public void synchronizeScreens() {
		Screen activeScreen = getCurrentActiveScreen();
		Screen inactiveScreen = getNonActiveScreen();
		CurrentPage activePage = CurrentPageManager.getInstance(activeScreen).getCurrentPage();
		CurrentPage inactivePage = CurrentPageManager.getInstance(inactiveScreen).getCurrentPage();
		Key targetActiveScreenKey = activePage.getSingleKey();
		Key inactiveScreenKey = inactivePage.getSingleKey();
		boolean isFirstTimeInit = (lastSynchdInactiveScreenKey==null);
		boolean inactiveUpdated = false;
		boolean isTotalRefreshRequired = isFirstTimeInit ||	lastSynchWasInNightMode!=ScreenSettings.isNightMode() || screenPreferencesChanged;

		if (isSplitScreensLinked()) {
			if ((isSplit() || isScreen2Minimized()) ) {
				// inactive screen may not be displayed but if switched to the key must be correct
				if (isSynchronizableVerseKey(activePage)) {
					// Only Bible and cmtry are synch'd and they share a Verse key
					updateInactiveBibleKey(inactiveScreen, targetActiveScreenKey);
					// re-get as it may have been mapped to the correct v11n
					targetActiveScreenKey = inactivePage.getSingleKey();
				}
			}

			if (isSplit() && !isScreen2Minimized()) {
				// prevent infinite loop as each screen update causes a synchronise by comparing last key
				// only update pages if empty or synchronised
				if (isFirstTimeInit || resynchRequired || 
				   (isSynchronizableVerseKey(activePage) && isSynchronizableVerseKey(inactivePage) && !targetActiveScreenKey.equals(lastSynchdInactiveScreenKey)) ) {
					updateInactiveScreen(inactiveScreen, inactivePage, targetActiveScreenKey, lastSynchdInactiveScreenKey, isTotalRefreshRequired);
					lastSynchdInactiveScreenKey = targetActiveScreenKey;
					inactiveUpdated = true;
				} 
			}
		}
			
		// force inactive screen to display something otherwise it may be initially blank
		// or if nightMode has changed then force an update
		if (!inactiveUpdated && isTotalRefreshRequired) {
			// force an update of the inactive page to prevent blank screen
			updateInactiveScreen(inactiveScreen, inactivePage, inactiveScreenKey, inactiveScreenKey, isTotalRefreshRequired);
			lastSynchdInactiveScreenKey = inactiveScreenKey;
			inactiveUpdated = true;
		}
		
		lastSynchWasInNightMode = ScreenSettings.isNightMode();
		screenPreferencesChanged = false;
		resynchRequired = false;
	}
	
	/** Only call if screens are synchronised.  Update synch'd keys even if inactive page not shown so if it is shown then it is correct
	 */
	private void updateInactiveBibleKey(Screen inactiveScreen, Key activeScreenKey) {
		CurrentPageManager.getInstance(inactiveScreen).getCurrentBible().doSetKey(activeScreenKey);
	}
	
	/** refresh/synch inactive screen if required
	 */
	private void updateInactiveScreen(Screen inactiveScreen, CurrentPage inactivePage,	Key targetScreenKey, Key inactiveScreenKey, boolean forceRefresh) {
		// standard null checks
		if (targetScreenKey!=null && inactivePage!=null) {
			// Not just bibles and commentaries get this far so NOT always fine to convert key to verse
			Verse targetVerse = null;
			Versification targetV11n = null;
			if (targetScreenKey instanceof Verse) {
				targetVerse = KeyUtil.getVerse(targetScreenKey);
				targetV11n = targetVerse.getVersification();
			}
			
			Verse currentVerse = null;
			if (inactiveScreenKey!=null && inactiveScreenKey instanceof Verse) {
				currentVerse = KeyUtil.getVerse(inactiveScreenKey);
			}
			
			// update split screen as smoothly as possible i.e. just jump/scroll if verse is on current page
			//TODO av11n
			if (!forceRefresh && 
					BookCategory.BIBLE.equals(inactivePage.getCurrentDocument().getBookCategory()) && 
					currentVerse!=null && targetVerse!=null && targetV11n.isSameChapter(targetVerse, currentVerse)) {
				EventBus.getDefault().post(new ScrollSecondaryScreenEvent(inactiveScreen, targetVerse.getVerse()));
			} else {
				new UpdateInactiveScreenTextTask().execute(inactiveScreen);
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
    	protected void showText(String text, Screen screen, int verseNo, float yOffsetRatio) {
    		EventBus.getDefault().post(new UpdateSecondaryScreenEvent(screen, text, verseNo));
        }
    }

	private void restoreFromSettings() {
		Log.d(TAG, "Refresh split screen settings");
		String splitScreenPreference = PREFS_SPLIT_SCREEN_SINGLE;
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			splitScreenPreference = preferences.getString(SPLIT_SCREEN_PREF, PREFS_SPLIT_SCREEN_SINGLE);
		}
		
		if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_SINGLE)) {
			isSplit = false;
			isSplitScreensLinked = false;
			screenManager.getScreen(1).setState(ScreenState.MAXIMISED);
			screenManager.getScreen(1).setSynchronised(false);
			screen1Weight = 0.5f;
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_LINKED)) {
			screenManager.getScreen(1).setState(ScreenState.SPLIT);
			screenManager.getScreen(2).setState(ScreenState.SPLIT);
			screenManager.getScreen(1).setSynchronised(true);
			screenManager.getScreen(2).setSynchronised(true);
			isSplit = true;
			isSplitScreensLinked = true;
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_NOT_LINKED)) {
			screenManager.getScreen(1).setState(ScreenState.SPLIT);
			screenManager.getScreen(2).setState(ScreenState.SPLIT);
			screenManager.getScreen(1).setSynchronised(false);
			screenManager.getScreen(2).setSynchronised(false);
			isSplit = true;
			isSplitScreensLinked = false;
		} else {
			// unexpected value so default to no split
			isSplit = false;
			isSplitScreensLinked = false;
			screenManager.getScreen(1).setState(ScreenState.MAXIMISED);
			screenManager.getScreen(1).setSynchronised(false);
			screen1Weight = 0.5f;
		}
	}
	
	/**
	 * Save/restore dynamic state that is not automatically saved as Preferences
	 */
	public void onEvent(AppToBackgroundEvent event) {
		if (event.isMovedToBackground()) {
			saveNonPreferenceState();
			// ensure nonactive screen is initialised when returning from background
			lastSynchdInactiveScreenKey = null;
		} else {
			lastSynchdInactiveScreenKey = null;
			restoreNonPreferenceState();
		}
	}
	
	private void restoreNonPreferenceState() {
		Log.d(TAG, "Refresh split non pref state");
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			screen1Weight = preferences.getFloat(SPLIT_SCREEN1_WEIGHT, 0.5f);
			if (preferences.getBoolean(SPLIT_SCREEN2_MINIMIZED, false)) {
				screenManager.getScreen(2).setState(ScreenState.MINIMISED);
			} else {
				screenManager.getScreen(2).setState(ScreenState.SPLIT);
			}
		}
	}
	
	private void saveNonPreferenceState() {
		Log.d(TAG, "Save split non pref state");
		CommonUtils.getSharedPreferences().edit()
										.putFloat(SPLIT_SCREEN1_WEIGHT, screen1Weight)
										.putBoolean(SPLIT_SCREEN2_MINIMIZED, ScreenState.MINIMISED==screenManager.getScreen(2).getState())
										.commit();
	}

	public boolean isSplit() {
		return isSplit;
	}
	public void setSplit(boolean isSplit) {
		if (this.isSplit!=isSplit) {
			this.isSplit = isSplit;
			// if split is false or true then it can no longer be minimised
			screenManager.getScreen(2).setState(ScreenState.SPLIT);
			if (isSplit) {
				synchronizeScreens();
			}
		}
	}

	public Screen getNonActiveScreen() {
		if (screenManager.getCurrentActiveScreen().getScreenNo()==1) {
			return screenManager.getScreen(2);
		} else {
			return screenManager.getScreen(1);
		}
	}
	public Screen getCurrentActiveScreen() {
		return screenManager.getCurrentActiveScreen();
	}
	public void setCurrentActiveScreen(Screen currentActiveScreen) {
		Log.d(TAG, "setCurrentActiveScreen:"+currentActiveScreen);
		if (currentActiveScreen != screenManager.getCurrentActiveScreen()) {
			screenManager.setCurrentActiveScreen(currentActiveScreen);
			EventBus.getDefault().post(new CurrentSplitScreenChangedEvent(currentActiveScreen));
		}
	}

	public boolean isSplitScreensLinked() {
		return isSplitScreensLinked;
	}
	public void setSplitScreensLinked(boolean isSplitScreensLinked) {
		this.isSplitScreensLinked = isSplitScreensLinked;
	}
	
	public boolean isScreen2Minimized() {
		return screenManager.getScreen(2).getState()==ScreenState.MINIMISED;
	}

	public boolean isSeparatorMoving() {
		// allow 1 sec for screen to settle after splitscreen drag
		if (stoppedMovingTime>0) {
			// allow a second after stopping for screen to settle
			if (stoppedMovingTime+SCREEN_SETTLE_TIME_MILLIS>System.currentTimeMillis()) {
				return true;
			}
			stoppedMovingTime = 0;
		}
		return isSeparatorMoving;
	}
	
	public void setSeparatorMoving(boolean isSeparatorMoving) {
		if (!isSeparatorMoving) {
			// facilitate time for the screen to settle
			this.stoppedMovingTime = System.currentTimeMillis();
		}
		this.isSeparatorMoving = isSeparatorMoving;
		
		boolean isMoveFinished = !isSeparatorMoving;
		if (isMoveFinished) {
			resynchRequired = true;
		}
		
		EventBus.getDefault().post(new SplitScreenSizeChangedEvent(isMoveFinished, getScreenVerseMap()));
	}

	/**
	 * Weight reflects the relative size of each screen
	 */
	public float getScreen1Weight() {
		return screen1Weight;
	}
	public void setScreen1Weight(float screen1Weight) {
		this.screen1Weight = screen1Weight;
	}

	/**
	 * Get current verse for each screen displaying a Bible
	 * 
	 * @return Map of screen num to verse num
	 */
	private Map<Screen, Integer> getScreenVerseMap() {
		// get page offsets to maintain for each screen
		Map<Screen,Integer> screenVerseMap = new HashMap<Screen,Integer>();
		for (Screen screen : screenManager.getScreens()) {
			CurrentPage currentPage = getCurrentPage(screen);
			if (currentPage!=null &&
				BookCategory.BIBLE == currentPage.getCurrentDocument().getBookCategory()) {
				int verse = KeyUtil.getVerse(currentPage.getSingleKey()).getVerse();
				screenVerseMap.put(screen, verse);
			}
		}
		return screenVerseMap;
	}
	
	/** Get Page info for each Screen 
	 */
	private CurrentPage getCurrentPage(Screen screenNo) {
		return CurrentPageManager.getInstance(screenNo).getCurrentPage();
	}
}
