package net.bible.android.control.page.splitscreen;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.control.event.splitscreen.NumberOfWindowsChangedEvent;
import net.bible.android.control.event.splitscreen.SplitScreenSizeChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
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

	private WindowRepository windowRepository;
	private SplitScreenSync splitScreenSync;
	
	public static int SCREEN_SETTLE_TIME_MILLIS = 1000;
	
	public static final String SPLIT_SCREEN_PREF = "split_screen_pref";
	private static final String PREFS_SPLIT_SCREEN_SINGLE = "single";
	private static final String PREFS_SPLIT_SCREEN_LINKED = "linked";
	private static final String PREFS_SPLIT_SCREEN_NOT_LINKED = "not_linked";
	
	private static final String TAG = "SplitScreenControl";
	
	private OnSharedPreferenceChangeListener onSettingsChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,	String key) {
			if (SPLIT_SCREEN_PREF.equals(key)) {
				splitScreenPreferenceChanged();
			} else {
				Log.d(TAG, "screen preferences changed so inactive screen needs to be refreshed");
				splitScreenSync.setScreenPreferencesChanged(true);
			}
			
		}
	};
	
	public SplitScreenControl(WindowRepository windowRepository) {
		this.windowRepository = windowRepository;
		splitScreenSync = new SplitScreenSync(windowRepository);
		
		// the listener needs to be a class variable because it is held in a WeakHashMap by SharedPreferences
		CommonUtils.getSharedPreferences().registerOnSharedPreferenceChangeListener(onSettingsChangeListener);
	}

	public Window getWindow(int windowNo) {
		return windowRepository.getWindow(windowNo);
	}
	public Window getActiveWindow() {
		return windowRepository.getCurrentActiveWindow();
	}
	public boolean isCurrentActiveWindow(Window currentActiveWindow) {
		return currentActiveWindow == windowRepository.getCurrentActiveWindow();
	}
	
	public void showLink(Book document, Key key) {
        Window linksWindow = windowRepository.getDedicatedLinksWindow();
        
        //TODO do not set links window active -  currently need to set links window to active window otherwise BibleContentMediator logic does not refresh that window
        windowRepository.setCurrentActiveWindow(linksWindow);
        
        linksWindow.getPageManager().setCurrentDocumentAndKey(document, key);
        
		// redisplay the current page
        if (!linksWindow.isVisible()) {
        	linksWindow.getWindowLayout().setState(WindowState.SPLIT);
        	EventBus.getDefault().post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
        }
	}

	public void addNewWindow() {
		//Window newScreen = 
		windowRepository.addNewWindow();

		// redisplay the current page
		EventBus.getDefault().post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));

		splitScreenSync.setResynchRequired(true);
		splitScreenSync.synchronizeScreens();
	}

	public void minimiseWindow(Window window) {
		windowRepository.minimise(window);

		//TODO may have to maximise another screen if there is only 1 screen unminimised

		// redisplay the current page
		EventBus.getDefault().post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
	}

	public void removeWindow(Window window) {
		windowRepository.remove(window);

		//TODO may have to maximise another screen if there is only 1 screen unminimised

		// redisplay the current page
		EventBus.getDefault().post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
	}

	public void restoreWindow(Window window) {
		window.getWindowLayout().setState(WindowState.SPLIT);
		
		// any maximised screen must be normalised
//		for (Window maxScreen :windowRepository.getMaximisedScreens()) {
//			maxScreen.setState(WindowState.SPLIT);
//		}
		
		// causes BibleViews to be created and laid out
		EventBus.getDefault().post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		
		splitScreenSync.setResynchRequired(true);
		splitScreenSync.synchronizeScreens();
	}

	/** screen orientation has changed */
	public void orientationChange() {
		// causes BibleViews to be created and laid out
		EventBus.getDefault().post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
	}
	
	public void synchronizeScreens() {
		splitScreenSync.synchronizeScreens();
	}
	
//TODO save all screen settings - but save it somewhere screen specific not in this control
	private void splitScreenPreferenceChanged() {
		Log.d(TAG, "Refresh split screen settings");
		String splitScreenPreference = PREFS_SPLIT_SCREEN_SINGLE;
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			splitScreenPreference = preferences.getString(SPLIT_SCREEN_PREF, PREFS_SPLIT_SCREEN_SINGLE);
		}
		
		if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_SINGLE)) {
			windowRepository.getWindow(1).getWindowLayout().setState(WindowState.SPLIT); //Was MAXIMIZED
			windowRepository.getWindow(1).setSynchronised(false);
			windowRepository.getWindow(1).getWindowLayout().setWeight(1f);
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_LINKED)) {
			windowRepository.getWindow(1).getWindowLayout().setState(WindowState.SPLIT);
			windowRepository.getWindow(2).getWindowLayout().setState(WindowState.SPLIT);
			windowRepository.getWindow(1).setSynchronised(true);
			windowRepository.getWindow(2).setSynchronised(true);
			//TODO should the other screens also be synchronised?
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_NOT_LINKED)) {
			windowRepository.getWindow(1).getWindowLayout().setState(WindowState.SPLIT);
			windowRepository.getWindow(2).getWindowLayout().setState(WindowState.SPLIT);
			windowRepository.getWindow(1).setSynchronised(false);
			windowRepository.getWindow(2).setSynchronised(false);
		}
	}
	
	public boolean isSplit() {
		return windowRepository.isMultiWindow();
	}

	public Window getCurrentActiveWindow() {
		return windowRepository.getCurrentActiveWindow();
	}
	public void setCurrentActiveWindow(Window currentActiveWindow) {
		windowRepository.setCurrentActiveWindow(currentActiveWindow);
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
		
		EventBus.getDefault().post(new SplitScreenSizeChangedEvent(isMoveFinished, getWindowVerseMap()));
	}

	public WindowRepository getWindowManager() {
		return windowRepository;
	}

	/**
	 * Get current verse for each window displaying a Bible
	 * 
	 * @return Map of window num to verse num
	 */
	private Map<Window, Integer> getWindowVerseMap() {
		// get page offsets to maintain for each window
		Map<Window,Integer> windowVerseMap = new HashMap<Window,Integer>();
		for (Window window : windowRepository.getWindows()) {
			CurrentPage currentPage = getCurrentPage(window);
			if (currentPage!=null &&
				BookCategory.BIBLE == currentPage.getCurrentDocument().getBookCategory()) {
				int verse = KeyUtil.getVerse(currentPage.getSingleKey()).getVerse();
				windowVerseMap.put(window, verse);
			}
		}
		return windowVerseMap;
	}
	
	/** Get Page info for each Window 
	 */
	private CurrentPage getCurrentPage(Window window) {
		return window.getPageManager().getCurrentPage();
	}
}
