package net.bible.android.control.page.splitscreen;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.control.event.splitscreen.CurrentSplitScreenChangedEvent;
import net.bible.android.control.page.splitscreen.Screen.ScreenState;
import de.greenrobot.event.EventBus;

public class ScreenRepository {

	// 1 based screen no
	private Screen currentActiveScreen;
	
	private List<Screen> screenList;
	
	public ScreenRepository() {
		screenList = new ArrayList<Screen>();
		currentActiveScreen = getScreen(1);
	}
	
	public List<Screen> getScreens() {
		return screenList;
	}

	public List<Screen> getVisibleScreens() {
		List<Screen> screens = new ArrayList<>();
		for (Screen screen : screenList) {
			// only 1 screen can be maximised
			if (screen.getState() == ScreenState.MAXIMISED) {
				screens.clear();
				screens.add(screen);
				return screens;
			} else if (screen.getState()==ScreenState.SPLIT) {
				screens.add(screen);
			}
		}
		return screens;
	}

	public List<Screen> getMinimisedScreens() {
		return getScreens(ScreenState.MINIMISED);
	}

	public List<Screen> getMaximisedScreens() {
		return getScreens(ScreenState.MAXIMISED);
	}

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
		return addNewScreen(screenList.size()+1);
	}
	
	private Screen addNewScreen(int screenNo) {
		Screen newScreen = new Screen(screenNo, getDefaultState());
		screenList.add(newScreen);
		return newScreen;
	}
	
	public boolean isSplit() {
		return getVisibleScreens().size()>1;
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
		if (screenList.size()==0) {
			return ScreenState.MAXIMISED;
		} else {
			return ScreenState.SPLIT;
		}
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
		switch (visibleScreens.size()) {
		case 0:
			screen.setState(ScreenState.MAXIMISED);
			break;
		case 1:
			visibleScreens.get(0).setState(ScreenState.MAXIMISED);
			break;
		default:
			break;
		}

		// has the active screen been minimised?
		if (getCurrentActiveScreen().equals(screen) && visibleScreens.size()>0) {
			setCurrentActiveScreen(visibleScreens.get(0));
		}
	}
	
}
