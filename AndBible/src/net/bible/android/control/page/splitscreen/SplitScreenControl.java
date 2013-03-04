package net.bible.android.control.page.splitscreen;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventManager;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

public class SplitScreenControl {
	public enum Screen {SCREEN_1, SCREEN_2};
	
	private boolean isSplit;
	private boolean isScreen2Minimized;
	
	private boolean isSplitScreensLinked;
	
	private boolean isSeparatorMoving = false;
	private long stoppedMovingTime = 0;
	private float screen1Weight = 0.5f;

	private boolean resynchRequired = false;
	
	private Screen currentActiveScreen = Screen.SCREEN_1;
	
	private Key lastSynchdInactiveScreenKey;
	private boolean lastSynchWasInNightMode;
	
	private SplitScreenEventManager splitScreenEventManager = new SplitScreenEventManager();
	
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
			}
		}
	};
	
	public SplitScreenControl() {
		restoreNonPreferenceState();
		restoreFromSettings();
		
		CurrentActivityHolder.getInstance().addAppToBackgroundListener(new AppToBackgroundListener() {
			@Override
			public void applicationNowInBackground(AppToBackgroundEvent e) {
				saveNonPreferenceState();
				// ensure nonactive screen is initialised when returning from background
				lastSynchdInactiveScreenKey = null;
			}

			@Override
			public void applicationReturnedFromBackground(AppToBackgroundEvent e) {
				lastSynchdInactiveScreenKey = null;
				restoreNonPreferenceState();
			}
		});
		// the listener needs to be a class variable because it is held in a WeakHashMap by SharedPreferences
		CommonUtils.getSharedPreferences().registerOnSharedPreferenceChangeListener(onSettingsChangeListener);
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
		splitScreenEventManager.numberOfScreensChanged(getScreenVerseMap());
	}

	public void restoreScreen2() {
		isScreen2Minimized = false;
		currentActiveScreen = Screen.SCREEN_1;
		isSplit = true;
		// causes BibleViews to be created and laid out
		splitScreenEventManager.numberOfScreensChanged(getScreenVerseMap());
		synchronizeScreens();
	}

	/** screen orientation has changed */
	public void orientationCange() {
		// causes BibleViews to be created and laid out
		splitScreenEventManager.numberOfScreensChanged(getScreenVerseMap());
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

		if (isSplitScreensLinked()) {
			if ((isSplit() || isScreen2Minimized()) ) {
				// inactive screen may not be displayed but if switched to the key must be correct
				// Only Bible and cmtry are synch'd and they share a Verse key
				updateInactiveBibleKey();
			}

			if (isSplit() && !isScreen2Minimized()) {
				// prevent infinite loop as each screen update causes a synchronise by comparing last key
				// only update pages if empty or synchronised
				if (isFirstTimeInit || resynchRequired || 
				   (isSynchronizable(activePage) && isSynchronizable(inactivePage) && !lastSynchdInactiveScreenKey.equals(activeScreenKey)) ) {
					updateInactiveScreen(inactivePage, activeScreenKey, inactiveScreenKey, isTotalRefreshRequired);
					lastSynchdInactiveScreenKey = activeScreenKey;
					inactiveUpdated = true;
				} 
			}
		}
			
		// force inactive screen to display something otherwise it may be initially blank
		// or if nightMode has changed then force an update
		if (!inactiveUpdated && isTotalRefreshRequired) {
			// force an update of the inactive page to prevent blank screen
			updateInactiveScreen(inactivePage, inactiveScreenKey, inactiveScreenKey, isTotalRefreshRequired);
			lastSynchdInactiveScreenKey = inactiveScreenKey;
			inactiveUpdated = true;
		}
		
		lastSynchWasInNightMode = ScreenSettings.isNightMode();
		resynchRequired = false;
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
		
		// update split screen as smoothly as possible i.e. just jump/scroll if verse is on current page
		if (!forceRefresh && BookCategory.BIBLE.equals(inactivePage.getCurrentDocument().getBookCategory()) && 
				targetVerse.isSameChapter(currentVerse)) {
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
			isScreen2Minimized = false;
			screen1Weight = 0.5f;
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_LINKED)) {
			isSplit = !isScreen2Minimized;
			isSplitScreensLinked = true;
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_NOT_LINKED)) {
			isSplit = !isScreen2Minimized;
			isSplitScreensLinked = false;
		} else {
			// unexpected value so default to no split
			isSplit = false;
			isSplitScreensLinked = false;
			isScreen2Minimized = false;
			screen1Weight = 0.5f;
		}
	}
	
	private void restoreNonPreferenceState() {
		Log.d(TAG, "Refresh split non pref state");
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			screen1Weight = preferences.getFloat(SPLIT_SCREEN1_WEIGHT, 0.5f);
			isScreen2Minimized = preferences.getBoolean(SPLIT_SCREEN2_MINIMIZED, false);
		}
	}
	
	private void saveNonPreferenceState() {
		Log.d(TAG, "Save split non pref state");
		CommonUtils.getSharedPreferences().edit()
										.putFloat(SPLIT_SCREEN1_WEIGHT, screen1Weight)
										.putBoolean(SPLIT_SCREEN2_MINIMIZED, isScreen2Minimized)
										.commit();
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

	public boolean isSeparatorMoving() {
		// allow 1 sec for screen to settle after splitscreen drag
		if (stoppedMovingTime>0) {
			// allow a second after stopping for screen to settle
			if (stoppedMovingTime+SCREEN_SETTLE_TIME_MILLIS>System.currentTimeMillis()) {
				Log.d(TAG, "*** seperator stopped moving but settle time NOT passed");
				return true;
			}
			Log.d(TAG, "*** seperator stopped moving and settle time passed stopped:"+stoppedMovingTime+" current:"+System.currentTimeMillis());
			stoppedMovingTime = 0;
		}
		return isSeparatorMoving;
	}
	
	public void setSeparatorMoving(boolean isSeparatorMoving) {
		if (!isSeparatorMoving) {
			// facilitate time for the screen to settle
			this.stoppedMovingTime = System.currentTimeMillis();
			Log.d(TAG, "*** stopped moving:"+System.currentTimeMillis());
		}
		this.isSeparatorMoving = isSeparatorMoving;
		
		boolean isMoveFinished = !isSeparatorMoving;
		if (isMoveFinished) {
			resynchRequired = true;
		}
		
		splitScreenEventManager.splitScreenSizeChange(isMoveFinished, getScreenVerseMap());
	}

	public float getScreen1Weight() {
		return screen1Weight;
	}
	public void setScreen1Weight(float screen1Weight) {
		this.screen1Weight = screen1Weight;
	}

	/**
	 * @return
	 */
	private Map<Screen, Integer> getScreenVerseMap() {
		// get page offsets to maintain for each screen
		Map<Screen,Integer> screenVerseMap = new HashMap<Screen,Integer>();
		for (Screen screen : Screen.values()) {
			if (BookCategory.BIBLE == getCurrentPage(screen).getCurrentDocument().getBookCategory()) {
				int verse = KeyUtil.getVerse(getCurrentPage(screen).getSingleKey()).getVerse();
				screenVerseMap.put(screen, verse);
				Log.d(TAG, screen+"* registered verse no:"+verse);
			} else {
				Log.e(TAG, screen+"* prob getting registered verse for doc:"+getCurrentPage(screen).getCurrentDocument());
			}
		}
		return screenVerseMap;
	}
	
	private CurrentPage getCurrentPage(Screen screenNo) {
		return CurrentPageManager.getInstance(screenNo).getCurrentPage();
	}
}
