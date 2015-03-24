package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.Window;

/**
 * Focus has been changed
 */
public class CurrentSplitScreenChangedEvent implements SplitScreenEvent {

	private Window activeScreen;
	
	public CurrentSplitScreenChangedEvent(Window activeScreen) {
		this.activeScreen = activeScreen;
	}

	public Window getActiveScreen() {
		return activeScreen;
	}
}
