package net.bible.android.control.page.splitscreen;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.passage.CurrentVerseChangedEvent;
import net.bible.android.control.event.splitscreen.NumberOfWindowsChangedEvent;
import net.bible.android.control.event.splitscreen.SplitScreenSizeChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.common.TestUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Central control of Split screens especially synchronization
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class WindowControl {

	private boolean isSeparatorMoving = false;
	private long stoppedMovingTime = 0;

	private WindowRepository windowRepository;
	private SplitScreenSync splitScreenSync;

	private EventManager eventManager;
	
	public static int SCREEN_SETTLE_TIME_MILLIS = 1000;
	
	public static final String SPLIT_SCREEN_PREF = "split_screen_pref";
	private static final String PREFS_SPLIT_SCREEN_SINGLE = "single";
	private static final String PREFS_SPLIT_SCREEN_LINKED = "linked";
	private static final String PREFS_SPLIT_SCREEN_NOT_LINKED = "not_linked";
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	private OnSharedPreferenceChangeListener onSettingsChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,	String key) {
			if (SPLIT_SCREEN_PREF.equals(key)) {
				splitScreenPreferenceChanged();
			} else {
				logger.debug("screen preferences changed so inactive screen needs to be refreshed");
				splitScreenSync.setScreenPreferencesChanged(true);
			}
			
		}
	};
	
	public WindowControl(WindowRepository windowRepository, EventManager eventManager) {
		this.eventManager = eventManager;
		this.windowRepository = windowRepository;
		splitScreenSync = new SplitScreenSync(windowRepository);
		
		if (TestUtils.isAndroid()) {
			// the listener needs to be a class variable because it is held in a WeakHashMap by SharedPreferences
			CommonUtils.getSharedPreferences().registerOnSharedPreferenceChangeListener(onSettingsChangeListener);
		}
		
		eventManager.register(this);
	}

	public void updateOptionsMenu(Menu menu) {
		MenuItem synchronisedMenuItem = menu.findItem(R.id.splitLink);
		if (synchronisedMenuItem!=null) {
			synchronisedMenuItem.setChecked(getActiveWindow().isSynchronised());
		}
	}
	
	public boolean isActiveWindow(Window window) {
		return window == windowRepository.getActiveWindow();
	}
	
	public void showLink(Book document, Key key) {
        Window linksWindow = windowRepository.getDedicatedLinksWindow();
        boolean linksWindowWasVisible = linksWindow.isVisible();
        
        //TODO do not set links window active -  currently need to set links window to active window otherwise BibleContentMediator logic does not refresh that window
        windowRepository.setActiveWindow(linksWindow);
        
        linksWindow.getPageManager().setCurrentDocumentAndKey(document, key);
        
		// redisplay the current page
        if (!linksWindowWasVisible) {
        	linksWindow.getWindowLayout().setState(WindowState.SPLIT);
        	eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
        }
	}

	public Window addNewWindow() {
		//Window newScreen = 
		Window window = windowRepository.addNewWindow();

		splitScreenSync.setResynchRequired(true);
		splitScreenSync.synchronizeScreens();
		
		// redisplay the current page
		eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));

		return window;
	}

	/**
	 * Minimise window if possible
	 */
	public void minimiseWindow(Window window) {
		if (windowRepository.getVisibleWindows().size()>1) {
			windowRepository.minimise(window);
	
			//TODO may have to maximise another screen if there is only 1 screen unminimised
	
			// redisplay the current page
			eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		}
	}

	public void removeCurrentWindow() {
		removeWindow(getActiveWindow());
	}
	public void removeWindow(Window window) {
		if (windowRepository.getVisibleWindows().size()>1 || !window.isVisible()) {
			logger.debug("Removing window "+window.getScreenNo());
			windowRepository.remove(window);
	
			//TODO may have to maximise another screen if there is only 1 screen unminimised
	
			// redisplay the current page
			eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		}
	}

	public void restoreWindow(Window window) {
		window.getWindowLayout().setState(WindowState.SPLIT);
		
		// any maximised screen must be normalised
//		for (Window maxScreen :windowRepository.getMaximisedScreens()) {
//			maxScreen.setState(WindowState.SPLIT);
//		}
		
		// causes BibleViews to be created and laid out
		eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		
		splitScreenSync.setResynchRequired(true);
		splitScreenSync.synchronizeScreens();
	}

	public void synchroniseCurrentWindow() {
		getActiveWindow().setSynchronised(true);

		splitScreenSync.setResynchRequired(true);
		splitScreenSync.synchronizeScreens();
	}
	
	public void unsynchroniseCurrentWindow() {
		getActiveWindow().setSynchronised(false);
	}
	
	/*
	 * Move the current window to first 
	 */
	public void promoteCurrentWindow() {
		Window window = getActiveWindow();

		windowRepository.moveWindowToPosition(window, 0);
	
		// redisplay the current page
		eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
	}


	/** screen orientation has changed */
	public void orientationChange() {
		// causes BibleViews to be created and laid out
		eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
	}
	
	public void onEvent(CurrentVerseChangedEvent event) {
		splitScreenSync.synchronizeScreens();
	}
	
//TODO save all screen settings - but save it somewhere screen specific not in this control
	private void splitScreenPreferenceChanged() {
		logger.debug("Refresh split screen settings");
		String splitScreenPreference = PREFS_SPLIT_SCREEN_SINGLE;
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			splitScreenPreference = preferences.getString(SPLIT_SCREEN_PREF, PREFS_SPLIT_SCREEN_SINGLE);
		}
		
		if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_SINGLE)) {
			windowRepository.getActiveWindow().getWindowLayout().setState(WindowState.SPLIT); //Was MAXIMIZED
			windowRepository.getActiveWindow().setSynchronised(false);
			windowRepository.getActiveWindow().getWindowLayout().setWeight(1f);
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_LINKED)) {
			for (Window window : windowRepository.getWindows()) {
				window.getWindowLayout().setState(WindowState.SPLIT);
				window.setSynchronised(true);
			}
		} else if (splitScreenPreference.equals(PREFS_SPLIT_SCREEN_NOT_LINKED)) {
			for (Window window : windowRepository.getWindows()) {
				window.getWindowLayout().setState(WindowState.SPLIT);
				window.setSynchronised(false);
			}
		}
	}
	
	public boolean isSplit() {
		return windowRepository.isMultiWindow();
	}

	public Window getActiveWindow() {
		return windowRepository.getActiveWindow();
	}
	public void setActiveWindow(Window currentActiveWindow) {
		windowRepository.setActiveWindow(currentActiveWindow);
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
		
		eventManager.post(new SplitScreenSizeChangedEvent(isMoveFinished, getWindowVerseMap()));
	}

	public WindowRepository getWindowRepository() {
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
			CurrentPage currentPage = window.getPageManager().getCurrentPage();
			if (currentPage!=null &&
				BookCategory.BIBLE == currentPage.getCurrentDocument().getBookCategory()) {
				int verse = KeyUtil.getVerse(currentPage.getSingleKey()).getVerse();
				windowVerseMap.put(window, verse);
			}
		}
		return windowVerseMap;
	}
}
