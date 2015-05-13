package net.bible.android.control.page.splitscreen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.passage.CurrentVerseChangedEvent;
import net.bible.android.control.event.splitscreen.NumberOfWindowsChangedEvent;
import net.bible.android.control.event.splitscreen.SplitScreenSizeChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.Logger;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;

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
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	public WindowControl(WindowRepository windowRepository, EventManager eventManager) {
		this.eventManager = eventManager;
		this.windowRepository = windowRepository;
		splitScreenSync = new SplitScreenSync(windowRepository);
		
		eventManager.register(this);
	}

	/**
	 * Set the synchronised checkbox in the app menu before displayed
	 * Disable various menu items if links window selected
	 */
	public void updateOptionsMenu(Menu menu) {
		MenuItem synchronisedMenuItem = menu.findItem(R.id.splitLink);
		MenuItem moveFirstMenuItem = menu.findItem(R.id.splitMoveFirst);
		MenuItem removeMenuItem = menu.findItem(R.id.splitDelete);
		Window window = getActiveWindow();

		if (synchronisedMenuItem!=null && moveFirstMenuItem!=null) {
			// set synchronised checkbox state
			synchronisedMenuItem.setChecked(window.isSynchronised());
		
			// the dedicated links window cannot be treated as a normal window
			boolean isDedicatedLinksWindowActive = isActiveWindow(windowRepository.getDedicatedLinksWindow());
			synchronisedMenuItem.setEnabled(!isDedicatedLinksWindowActive);
			moveFirstMenuItem.setEnabled(!isDedicatedLinksWindowActive);
			
			// cannot remove last normal window
			removeMenuItem.setEnabled(isWindowRemovable(window));

			// if window is already first then cannot promote
			List<Window> visibleWindows = windowRepository.getVisibleWindows();
			if (visibleWindows.size()>0 && window.equals(visibleWindows.get(0))) {
				moveFirstMenuItem.setEnabled(false);
			}
		}		
	}
	
	public boolean isActiveWindow(Window window) {
		return window.equals(windowRepository.getActiveWindow());
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
	public void minimiseCurrentWindow() {
		minimiseWindow(getActiveWindow());
	}
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
		
		if (isWindowRemovable(getActiveWindow())) {
			logger.debug("Removing window "+window.getScreenNo());
			windowRepository.remove(window);
	
			// redisplay the current page
			eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		}
	}
	
	public boolean isWindowRemovable(Window window) {
		int normalWindows = windowRepository.getVisibleWindows().size();
		if (windowRepository.getDedicatedLinksWindow().isVisible()) {
			normalWindows--;
		}
		
		return window.isLinksWindow() || normalWindows>1 || !window.isVisible();
	}

	public void restoreWindow(Window window) {
		window.getWindowLayout().setState(WindowState.SPLIT);
		
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
	public void moveCurrentWindowToFirst() {
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
