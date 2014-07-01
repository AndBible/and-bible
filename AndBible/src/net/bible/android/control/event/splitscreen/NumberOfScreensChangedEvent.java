package net.bible.android.control.event.splitscreen;

import java.util.Map;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

/**
 * 	Split screen has been minimized/restored/removed/added
 */
public class NumberOfScreensChangedEvent implements SplitScreenEvent {
	
	private Map<Screen, Integer> screenVerseMap;

	public NumberOfScreensChangedEvent(Map<Screen, Integer> screenVerseMap) {
		this.screenVerseMap = screenVerseMap;
	}

	public boolean isVerseNoSet(Screen screen) {
		return screenVerseMap.containsKey(screen);
	}

	public Integer getVerseNo(Screen screen) {
		return screenVerseMap.get(screen);
	}
}
