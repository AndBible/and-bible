package net.bible.android.control.page.splitscreen;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.splitscreen.CurrentSplitScreenChangedEvent;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.Logger;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import de.greenrobot.event.EventBus;

public class WindowRepository {

	// 1 based screen no
	private Window currentActiveScreen;
	
	private List<Window> windowList;
	
	private Window dedicatedLinksWindow;
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	public WindowRepository() {
		dedicatedLinksWindow = new Window(999, WindowState.SPLIT);
		dedicatedLinksWindow.setSynchronised(false);

		windowList = new ArrayList<Window>();
		currentActiveScreen = getWindow(1);

		// restore state from previous invocation
    	restoreState();
    	
		// listen for AppToBackgroundEvent to save state when moved to background
    	EventBus.getDefault().register(this);
	}
	
	public List<Window> getWindows() {
		List<Window> windows = new ArrayList<>(windowList);
		if (dedicatedLinksWindow.isVisible()) {
			windows.add(dedicatedLinksWindow);
		}
		return windows;
	}

	public List<Window> getVisibleScreens() {
			// only 1 screen can be maximised
//			if (screen.getState() == WindowState.MAXIMISED) {
//				screens.clear();
//				screens.add(screen);
//				return screens;
//			} else 
		return getWindows(WindowState.SPLIT);
	}

	public List<Window> getMinimisedScreens() {
		return getWindows(WindowState.MINIMISED);
	}

//	public List<Window> getMaximisedScreens() {
//		return getScreens(WindowState.MAXIMISED);
//	}

	private List<Window> getWindows(WindowState state) {
		List<Window> windows = new ArrayList<>();
		for (Window window : getWindows()) {
			if (window.getWindowLayout().getState() == state) {
				windows.add(window);
			}
		}
		return windows;
	}

	public Window getWindow(int screenNo) {
		for (Window window : getWindows()) {
			if (window.getScreenNo()==screenNo) {
				return window;
			}
		}
		return addNewScreen(screenNo);
	}
	
	public Window getDedicatedLinksWindow() {
		return dedicatedLinksWindow;
	}

	public Window addNewWindow() {
		// ensure main screen is not maximized
		getCurrentActiveWindow().getWindowLayout().setState(WindowState.SPLIT);

		return addNewScreen(windowList.size()+1);
	}
	
	private Window addNewScreen(int screenNo) {
		Window newScreen = new Window(screenNo, getDefaultState());
		windowList.add(newScreen);
		return newScreen;
	}
	
	public boolean isMultiWindow() {
		List<Window> windows = getVisibleScreens();
		return windows.size()>1;
	}
	
	public CurrentPageManager getCurrentPageManager() {
		return getCurrentActiveWindow().getPageManager();
	}
	
	public void setDefaultActiveScreen() {
		for (Window window : getWindows()) {
			if (window.isVisible()) {
				currentActiveScreen = window;
			}
		}
	}
	
	private WindowLayout.WindowState getDefaultState() {
		//TODO 
//		if (getWindows().size()==0) {
//			return WindowState.MAXIMISED;
//		} else {
			return WindowState.SPLIT;
//		}
	}

	public Window getCurrentActiveWindow() {
		return currentActiveScreen;
	}

	public void setCurrentActiveWindow(Window newActiveScreen) {
		if (currentActiveScreen != newActiveScreen) {
			this.currentActiveScreen = newActiveScreen;
			EventBus.getDefault().post(new CurrentSplitScreenChangedEvent(currentActiveScreen));
		}
	}
	
	public List<Window> getNonActiveScreenList() {
		List<Window> windows = getVisibleScreens();
		windows.remove(getCurrentActiveWindow());
		return windows;
	}
	
	public void minimise(Window window) {
		window.getWindowLayout().setState(WindowState.MINIMISED);

		// adjustments
		List<Window> visibleScreens = getVisibleScreens();
		// I don't think we need this
//		switch (visibleScreens.size()) {
//		case 0:
//			screen.setState(WindowState.MAXIMISED);
//			break;
//		case 1:
//			visibleScreens.get(0).setState(WindowState.MAXIMISED);
//			break;
//		default:
//			break;
//		}

		// has the active screen been minimised?
		if (getCurrentActiveWindow().equals(window) && visibleScreens.size()>0) {
			setCurrentActiveWindow(visibleScreens.get(0));
		}
	}

	public void remove(Window window) {
		windowList.remove(window);

		// adjustments
		List<Window> visibleScreens = getVisibleScreens();

		// has the active screen been minimised?
		if (getCurrentActiveWindow().equals(window) && visibleScreens.size()>0) {
			setCurrentActiveWindow(visibleScreens.get(0));
		}

	}

	/** 
	 * If app moves to background then save current state to allow continuation after return
	 * 
	 * @param appToBackgroundEvent Event info
	 */
	public void onEvent(AppToBackgroundEvent appToBackgroundEvent) {
		if (appToBackgroundEvent.isMovedToBackground()) {
			saveState();
		}
	}
    /** save current page and document state */
	protected void saveState() {
    	logger.info("Save instance state for screens");
    	SharedPreferences settings = BibleApplication.getApplication().getAppStateSharedPreferences();
		saveState(settings);
	}

	/** restore current page and document state */
    private void restoreState() {
    	try {
        	logger.info("Restore instance state for screens");
        	SharedPreferences settings = BibleApplication.getApplication().getAppStateSharedPreferences();
    		restoreState(settings);
    	} catch (Exception e) {
    		logger.error("Restore error", e);
    	}
    }
	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	private void saveState(SharedPreferences outState) {
		logger.info("save state");

		JSONArray allScreenState = new JSONArray();
		for (Window window : windowList) {
			try {
				allScreenState.put(window.getStateJson());
			} catch (JSONException je) {
				logger.error("Error saving screen state", je);
			}
		}
		
		SharedPreferences.Editor editor = outState.edit();
		editor.putString("screenStateArray", allScreenState.toString());
		editor.commit();
	}
	
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	private void restoreState(SharedPreferences inState) {
		logger.info("restore state");
		String allScreenStateString = inState.getString("screenStateArray", null);
		if (StringUtils.isNotEmpty(allScreenStateString)) {
			try {
				// remove current (default) state before restoring
				windowList.clear();
				
				JSONArray allScreenState = new JSONArray(allScreenStateString);
				for (int i=0; i<allScreenState.length(); i++) {
					try {
						JSONObject screenState = allScreenState.getJSONObject(i);
						Window window = new Window();
						window.restoreState(screenState);
						
						// prevent rubbish
						if (window.getScreenNo()==i+1) {
							windowList.add(window);
						}
					} catch (JSONException je) {
						logger.error("Error restoring screen state", je);
					}
				}
			} catch (JSONException je) {
				logger.error("Error restoring screen state", je);
			}
		}
	}
}
