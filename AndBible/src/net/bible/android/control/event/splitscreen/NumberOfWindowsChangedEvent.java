package net.bible.android.control.event.splitscreen;

import java.util.Map;

import net.bible.android.control.page.splitscreen.Window;

/**
 * 	Split screen has been minimized/restored/removed/added
 */
public class NumberOfWindowsChangedEvent implements SplitScreenEvent {
	
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
