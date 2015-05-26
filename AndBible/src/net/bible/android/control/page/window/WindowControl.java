package net.bible.android.control.page.window;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.passage.CurrentVerseChangedEvent;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.event.window.WindowSizeChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.WindowLayout.WindowState;
import net.bible.service.common.Logger;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * Central control of windows especially synchronization
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class WindowControl {

	private boolean isSeparatorMoving = false;
	private long stoppedMovingTime = 0;

	private WindowRepository windowRepository;
	private WindowSync windowSync;

	private EventManager eventManager;
	
	public static int SCREEN_SETTLE_TIME_MILLIS = 1000;
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	public WindowControl(WindowRepository windowRepository, EventManager eventManager) {
		this.eventManager = eventManager;
		this.windowRepository = windowRepository;
		
		windowSync = new WindowSync(windowRepository);
		
		eventManager.register(this);
	}

	/**
	 * Add the Window sub-menu resource which is not included in the main.xml for the main menu
	 * Set the synchronised checkbox in the app menu before displayed
	 * Disable various menu items if links window selected
	 */
	public void updateOptionsMenu(Menu menu) {
		// when updating main menu rather than Window options menu
		MenuItem windowSubmenuItemPosition = menu.findItem(R.id.windowSubMenu);
		if (windowSubmenuItemPosition!=null) {
			// check the Window sub-menu has been added
			Menu subMenuToPopulate = windowSubmenuItemPosition.getSubMenu();
			if (subMenuToPopulate.findItem(R.id.windowNew)==null) {
				new MenuInflater(BibleApplication.getApplication()).inflate(R.menu.window_popup_menu, subMenuToPopulate);
			}
		}
		
		MenuItem synchronisedMenuItem = menu.findItem(R.id.windowSynchronised);
		MenuItem moveFirstMenuItem = menu.findItem(R.id.windowMoveFirst);
		MenuItem closeMenuItem = menu.findItem(R.id.windowClose);
		MenuItem minimiseMenuItem = menu.findItem(R.id.windowMinimise);
		Window window = getActiveWindow();

		if (synchronisedMenuItem!=null && moveFirstMenuItem!=null) {
			// set synchronised checkbox state
			synchronisedMenuItem.setChecked(window.isSynchronised());
		
			// the dedicated links window cannot be treated as a normal window
			boolean isDedicatedLinksWindowActive = isActiveWindow(windowRepository.getDedicatedLinksWindow());
			synchronisedMenuItem.setEnabled(!isDedicatedLinksWindowActive);
			moveFirstMenuItem.setEnabled(!isDedicatedLinksWindowActive);
			
			// cannot close last normal window
			closeMenuItem.setEnabled(isWindowRemovable(window));
			minimiseMenuItem.setEnabled(isWindowMinimisable(window));

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
	
	/** 
	 * Show link using whatever is the current Bible in the Links window
	 */
	public void showLinkUsingDefaultBible(Key key) {
		Book defaultBible = windowRepository.getDedicatedLinksWindow().getPageManager().getCurrentBible().getCurrentDocument();
		showLink(defaultBible, key);
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

		windowSync.setResynchRequired(true);
		windowSync.synchronizeScreens();
		
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
		if (isWindowMinimisable(window)) {
			windowRepository.minimise(window);
	
			// redisplay the current page
			eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		}
	}

	public void closeCurrentWindow() {
		closeWindow(getActiveWindow());
	}
	public void closeWindow(Window window) {
		
		if (isWindowRemovable(getActiveWindow())) {
			logger.debug("Closing window "+window.getScreenNo());
			windowRepository.close(window);
	
			// redisplay the current page
			eventManager.post(new NumberOfWindowsChangedEvent(getWindowVerseMap()));
		}
	}
	
	public boolean isWindowMinimisable(Window window) {
		return isWindowRemovable(window) && !window.isLinksWindow();
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
		
		windowSync.setResynchRequired(true);
		windowSync.synchronizeScreens();
	}

	public void synchroniseCurrentWindow() {
		getActiveWindow().setSynchronised(true);

		windowSync.setResynchRequired(true);
		windowSync.synchronizeScreens();
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
		windowSync.synchronizeScreens();
	}
	
	public boolean isMultiWindow() {
		return windowRepository.isMultiWindow();
	}

	public Window getActiveWindow() {
		return windowRepository.getActiveWindow();
	}
	public void setActiveWindow(Window currentActiveWindow) {
		windowRepository.setActiveWindow(currentActiveWindow);
	}

	public boolean isSeparatorMoving() {
		// allow 1 sec for screen to settle after window separator drag
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
			windowSync.setResynchRequired(true);
		}
		
		eventManager.post(new WindowSizeChangedEvent(isMoveFinished, getWindowVerseMap()));
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
