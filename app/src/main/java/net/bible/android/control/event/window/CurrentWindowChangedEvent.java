package net.bible.android.control.event.window;

import net.bible.android.control.page.window.Window;

/**
 * Focus has been changed
 */
public class CurrentWindowChangedEvent implements WindowEvent {

	private Window activeWindow;
	
	public CurrentWindowChangedEvent(Window activeWindow) {
		this.activeWindow = activeWindow;
	}

	public Window getActiveWindow() {
		return activeWindow;
	}
}
