package net.bible.android.control.page.splitscreen;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.splitscreen.CurrentSplitScreenChangedEvent;
import net.bible.android.control.page.splitscreen.Screen.ScreenState;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.util.Log;
import de.greenrobot.event.EventBus;

public class ScreenRepository {

	// 1 based screen no
	private Screen currentActiveScreen;
	
	private List<Screen> screenList;
	
	private static final String TAG="ScreenRepository";
	
	public ScreenRepository() {
		screenList = new ArrayList<Screen>();
		currentActiveScreen = getScreen(1);
		
		// restore state from previous invocation
    	restoreState();
    	
		// listen for AppToBackgroundEvent to save state when moved to background
    	EventBus.getDefault().register(this);
	}
	
	public List<Screen> getScreens() {
		return screenList;
	}

	public List<Screen> getVisibleScreens() {
		List<Screen> screens = new ArrayList<>();
		for (Screen screen : screenList) {
			// only 1 screen can be maximised
//			if (screen.getState() == ScreenState.MAXIMISED) {
//				screens.clear();
//				screens.add(screen);
//				return screens;
//			} else 
			if (screen.getState()==ScreenState.SPLIT) {
				screens.add(screen);
			}
		}
		return screens;
	}

	public List<Screen> getMinimisedScreens() {
		return getScreens(ScreenState.MINIMISED);
	}

//	public List<Screen> getMaximisedScreens() {
//		return getScreens(ScreenState.MAXIMISED);
//	}

	private List<Screen> getScreens(ScreenState state) {
		List<Screen> screens = new ArrayList<>();
		for (Screen screen : screenList) {
			if (screen.getState() == state) {
				screens.add(screen);
			}
		}
		return screens;
	}

	public Screen getScreen(int screenNo) {
		for (Screen screen : screenList) {
			if (screen.getScreenNo()==screenNo) {
				return screen;
			}
		}
		return addNewScreen(screenNo);
	}

	public Screen addNewScreen() {
		// ensure main screen is not maximized
		getCurrentActiveScreen().setState(ScreenState.SPLIT);

		return addNewScreen(screenList.size()+1);
	}
	
	private Screen addNewScreen(int screenNo) {
		Screen newScreen = new Screen(screenNo, getDefaultState());
		screenList.add(newScreen);
		return newScreen;
	}
	
	public boolean isMultiScreen() {
		List<Screen> screens = getVisibleScreens();
		return screens.size()>1 && screens.get(0).getState()==ScreenState.SPLIT;
	}
	
	public void setDefaultActiveScreen() {
		for (Screen screen : screenList) {
			if (screen.getState() != ScreenState.MINIMISED) {
				currentActiveScreen = screen;
			}
		}
	}
	
	private Screen.ScreenState getDefaultState() {
		//TODO 
//		if (screenList.size()==0) {
//			return ScreenState.MAXIMISED;
//		} else {
			return ScreenState.SPLIT;
//		}
	}

	public Screen getCurrentActiveScreen() {
		return currentActiveScreen;
	}

	public void setCurrentActiveScreen(Screen newActiveScreen) {
		if (currentActiveScreen != newActiveScreen) {
			this.currentActiveScreen = newActiveScreen;
			EventBus.getDefault().post(new CurrentSplitScreenChangedEvent(currentActiveScreen));
		}
	}
	
	public List<Screen> getNonActiveScreenList() {
		List<Screen> screens = getVisibleScreens();
		screens.remove(getCurrentActiveScreen());
		return screens;
	}
	
	public void minimise(Screen screen) {
		screen.setState(ScreenState.MINIMISED);

		// adjustments
		List<Screen> visibleScreens = getVisibleScreens();
		// I don't think we need this
//		switch (visibleScreens.size()) {
//		case 0:
//			screen.setState(ScreenState.MAXIMISED);
//			break;
//		case 1:
//			visibleScreens.get(0).setState(ScreenState.MAXIMISED);
//			break;
//		default:
//			break;
//		}

		// has the active screen been minimised?
		if (getCurrentActiveScreen().equals(screen) && visibleScreens.size()>0) {
			setCurrentActiveScreen(visibleScreens.get(0));
		}
	}

	public void remove(Screen screen) {
		screenList.remove(screen);

		// adjustments
		List<Screen> visibleScreens = getVisibleScreens();

		// has the active screen been minimised?
		if (getCurrentActiveScreen().equals(screen) && visibleScreens.size()>0) {
			setCurrentActiveScreen(visibleScreens.get(0));
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
    	Log.i(TAG, "Save instance state for screens");
    	SharedPreferences settings = BibleApplication.getApplication().getAppStateSharedPreferences();
		saveState(settings);
	}

	/** restore current page and document state */
    private void restoreState() {
    	try {
        	Log.i(TAG, "Restore instance state for screens");
        	SharedPreferences settings = BibleApplication.getApplication().getAppStateSharedPreferences();
    		restoreState(settings);
    	} catch (Exception e) {
    		Log.e(TAG, "Restore error", e);
    	}
    }
	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	private void saveState(SharedPreferences outState) {
		Log.i(TAG, "save state");

		JSONArray allScreenState = new JSONArray();
		for (Screen screen : screenList) {
			try {
				allScreenState.put(screen.getStateJson());
			} catch (JSONException je) {
				Log.e(TAG, "Error saving screen state", je);
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
		Log.i(TAG, "restore state");
		String allScreenStateString = inState.getString("screenStateArray", null);
		if (StringUtils.isNotEmpty(allScreenStateString)) {
			try {
				// remove current (default) state before restoring
				screenList.clear();
				
				JSONArray allScreenState = new JSONArray(allScreenStateString);
				for (int i=0; i<allScreenState.length(); i++) {
					try {
						JSONObject screenState = allScreenState.getJSONObject(i);
						Screen screen = new Screen();
						screen.restoreState(screenState);
						
						// prevent rubbish
						if (screen.getScreenNo()==i+1) {
							screenList.add(screen);
						}
					} catch (JSONException je) {
						Log.e(TAG, "Error restoring screen state", je);
					}
				}
			} catch (JSONException je) {
				Log.e(TAG, "Error restoring screen state", je);
			}
		}
	}
}
