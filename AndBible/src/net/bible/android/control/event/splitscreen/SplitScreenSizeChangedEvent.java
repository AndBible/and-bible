package net.bible.android.control.event.splitscreen;

import java.util.Map;

import net.bible.android.control.page.splitscreen.Window;

/**
 * Split screen size changed - often due to separator being moved
 */
public class SplitScreenSizeChangedEvent implements SplitScreenEvent {

	private boolean isFinished;
	private Map<Window, Integer> screenVerseMap;
	
	public SplitScreenSizeChangedEvent(boolean isFinished, Map<Window, Integer> screenVerseMap) {
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
