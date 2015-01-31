package net.bible.android.control.page.splitscreen;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.control.page.splitscreen.Screen.ScreenState;

public class ScreenManager {

	// 1 based screen no
	private Screen currentActiveScreen;
	
	private List<Screen> screenList;
	
	public ScreenManager() {
		screenList = new ArrayList<Screen>();
		currentActiveScreen = getScreen(1);
	}
	
	public List<Screen> getScreens() {
		return screenList;
	}

	public Screen getScreen(int screenNo) {
		for (Screen screen : screenList) {
			if (screen.getScreenNo()==screenNo) {
				return screen;
			}
		}
		Screen newScreen = new Screen(screenNo, getDefaultState());
		screenList.add(newScreen);
		return newScreen;
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

	public void setCurrentActiveScreen(Screen currentActiveScreen) {
		this.currentActiveScreen = currentActiveScreen;
	}
	
	
}
