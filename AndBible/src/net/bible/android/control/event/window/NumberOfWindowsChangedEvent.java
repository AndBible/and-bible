package net.bible.android.control.event.window;

import java.util.Map;

import net.bible.android.control.page.window.Window;

/**
 * 	Window has been minimized/restored/removed/added
 */
public class NumberOfWindowsChangedEvent implements WindowEvent {
	
	private Map<Window, Integer> screenVerseMap;

	public NumberOfWindowsChangedEvent(Map<Window, Integer> screenVerseMap) {
		this.screenVerseMap = screenVerseMap;
	}

	public boolean isVerseNoSet(Window window) {
		return screenVerseMap.containsKey(window);
	}

	public Integer getVerseNo(Window window) {
		return screenVerseMap.get(window);
	}
}
