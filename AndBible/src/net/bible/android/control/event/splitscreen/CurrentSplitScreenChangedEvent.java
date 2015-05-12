package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.Window;

/**
 * Focus has been changed
 */
public class CurrentSplitScreenChangedEvent implements SplitScreenEvent {

	private Window activeWindow;
	
	public CurrentSplitScreenChangedEvent(Window activeWindow) {
		this.activeWindow = activeWindow;
	}

	public Window getActiveWindow() {
		return activeWindow;
	}
}
