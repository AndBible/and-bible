package net.bible.android.control.page.splitscreen;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.splitscreen.NumberOfScreensChangedEvent;
import net.bible.android.control.event.splitscreen.SplitScreenSizeChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.Screen.ScreenState;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.KeyUtil;

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

	private boolean isSeparatorMoving = false;
	private long stoppedMovingTime = 0;

	private ScreenRepository screenRepository = new ScreenRepository();
	private SplitScreenSync splitScreenSync = new SplitScreenSync(screenRepository);
	
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
				splitScreenSync.setScreenPreferencesChanged(true);
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
		return screenRepository.getScreen(screenNo);
	}
	public Screen getActiveScreen() {
		return screenRepository.getCurrentActiveScreen();
	}
	public boolean isCurrentActiveScreen(Screen currentActiveScreen) {
		return currentActiveScreen == screenRepository.getCurrentActiveScreen();
	}
	
	public void addNewScreen() {
		Screen newScreen = screenRepository.addNewScreen();

		// redisplay the current page
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
	}

	public void minimiseScreen(Screen screen) {
		screenRepository.minimise(screen);

		//TODO may have to maximise another screen if there is only 1 screen unminimised

		// redisplay the current page
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
	}

	public void restoreScreen(Screen screen) {
		screen.setState(ScreenState.SPLIT);
		
		// any maximised screen must be normalised
		for (Screen maxScreen :screenRepository.getMaximisedScreens()) {
			maxScreen.setState(ScreenState.SPLIT);
		}
		
		// causes BibleViews to be created and laid out
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
		
		splitScreenSync.synchronizeScreens();
	}

	/** screen orientation has changed */
	public void orientationChange() {
		// causes BibleViews to be created and laid out
		EventBus.getDefault().post(new NumberOfScreensChangedEvent(getScreenVerseMap()));
	}
	
	public void synchronizeScreens() {
		splitScreenSync.synchronizeScreens();
	}
	
//TODO save all screen settings - but save it somewhere screen specific not in this control
	private void restoreFromSettings() {
		Log.d(TAG, "Refresh split screen settings");
		String splitScreenPreference = PREFS_SPLIT_SCREEN_SINGLE;
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			splitScreenPreference = preferences.getString(SPLIT_SCREEN_PREF, PREFS_SPLIT_SCREEN_SINGLE);
		}
		
		if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_SINGLE)) {
			screenRepository.getScreen(1).setState(ScreenState.MAXIMISED);
			screenRepository.getScreen(1).setSynchronised(false);
			screenRepository.getScreen(1).setWeight(1f);
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_LINKED)) {
			screenRepository.getScreen(1).setState(ScreenState.SPLIT);
			screenRepository.getScreen(2).setState(ScreenState.SPLIT);
			screenRepository.getScreen(1).setSynchronised(true);
			screenRepository.getScreen(2).setSynchronised(true);
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_NOT_LINKED)) {
			screenRepository.getScreen(1).setState(ScreenState.SPLIT);
			screenRepository.getScreen(2).setState(ScreenState.SPLIT);
			screenRepository.getScreen(1).setSynchronised(false);
			screenRepository.getScreen(2).setSynchronised(false);
		} else {
			// unexpected value so default to no split
			screenRepository.getScreen(1).setState(ScreenState.MAXIMISED);
			screenRepository.getScreen(1).setSynchronised(false);
			screenRepository.getScreen(1).setWeight(1f);
		}
	}
	
	/**
	 * Save/restore dynamic state that is not automatically saved as Preferences
	 */
	public void onEvent(AppToBackgroundEvent event) {
		if (event.isMovedToBackground()) {
			saveNonPreferenceState();
		} else {
			restoreNonPreferenceState();
		}
	}
	
	private void restoreNonPreferenceState() {
		Log.d(TAG, "Refresh split non pref state");
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			screenRepository.getScreen(1).setWeight(preferences.getFloat(SPLIT_SCREEN1_WEIGHT, 1f));
			if (preferences.getBoolean(SPLIT_SCREEN2_MINIMIZED, false)) {
				screenRepository.getScreen(2).setState(ScreenState.MINIMISED);
			} else {
				screenRepository.getScreen(2).setState(ScreenState.SPLIT);
			}
		}
	}
	
	private void saveNonPreferenceState() {
		Log.d(TAG, "Save split non pref state");
		CommonUtils.getSharedPreferences().edit()
										.putFloat(SPLIT_SCREEN1_WEIGHT, screenRepository.getScreen(1).getWeight())
										.putBoolean(SPLIT_SCREEN2_MINIMIZED, ScreenState.MINIMISED==screenRepository.getScreen(2).getState())
										.commit();
	}

	public boolean isSplit() {
		return screenRepository.isSplit();
	}

	public Screen getNonActiveScreen() {
		if (screenRepository.getCurrentActiveScreen().getScreenNo()==1) {
			return screenRepository.getScreen(2);
		} else {
			return screenRepository.getScreen(1);
		}
	}
	public Screen getCurrentActiveScreen() {
		return screenRepository.getCurrentActiveScreen();
	}
	public void setCurrentActiveScreen(Screen currentActiveScreen) {
		screenRepository.setCurrentActiveScreen(currentActiveScreen);
	}

	public boolean isScreen2Minimized() {
		return screenRepository.getScreen(2).getState()==ScreenState.MINIMISED;
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
			splitScreenSync.setResynchRequired(true);
		}
		
		EventBus.getDefault().post(new SplitScreenSizeChangedEvent(isMoveFinished, getScreenVerseMap()));
	}

	public ScreenRepository getScreenManager() {
		return screenRepository;
	}

	/**
	 * Get current verse for each screen displaying a Bible
	 * 
	 * @return Map of screen num to verse num
	 */
	private Map<Screen, Integer> getScreenVerseMap() {
		// get page offsets to maintain for each screen
		Map<Screen,Integer> screenVerseMap = new HashMap<Screen,Integer>();
		for (Screen screen : screenRepository.getScreens()) {
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
