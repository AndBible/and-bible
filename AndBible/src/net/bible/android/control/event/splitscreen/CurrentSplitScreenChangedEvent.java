package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.Screen;

/**
 * Focus has been changed
 */
public class CurrentSplitScreenChangedEvent implements SplitScreenEvent {

	private Screen activeScreen;
	
	public CurrentSplitScreenChangedEvent(Screen activeScreen) {
		this.activeScreen = activeScreen;
	}

	public Screen getActiveScreen() {
		return activeScreen;
	}
}
