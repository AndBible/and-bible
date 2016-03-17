package net.bible.android.control.event.window;

import java.util.Map;

import net.bible.android.control.page.window.Window;

/**
 * Window size changed - often due to separator being moved
 */
public class WindowSizeChangedEvent implements WindowEvent {

	private boolean isFinished;
	private Map<Window, Integer> screenVerseMap;
	
	public WindowSizeChangedEvent(boolean isFinished, Map<Window, Integer> screenVerseMap) {
		this.isFinished = isFinished;
		this.screenVerseMap = screenVerseMap;
	}

	public boolean isFinished() {
		return isFinished;
	}

	public boolean isVerseNoSet(Window window) {
		return screenVerseMap.containsKey(window);
	}

	public Integer getVerseNo(Window window) {
		return screenVerseMap.get(window);
	}
}
