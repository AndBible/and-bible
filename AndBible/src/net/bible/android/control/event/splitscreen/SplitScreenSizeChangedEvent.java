package net.bible.android.control.event.splitscreen;

import java.util.Map;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

/**
 * Split screen size changed - often due to separator being moved
 */
public class SplitScreenSizeChangedEvent implements SplitScreenEvent {

	private boolean isFinished;
	private Map<Screen, Integer> screenVerseMap;
	
	public SplitScreenSizeChangedEvent(boolean isFinished, Map<Screen, Integer> screenVerseMap) {
		this.isFinished = isFinished;
		this.screenVerseMap = screenVerseMap;
	}

	public boolean isFinished() {
		return isFinished;
	}

	public boolean isVerseNoSet(Screen screen) {
		return screenVerseMap.containsKey(screen);
	}

	public Integer getVerseNo(Screen screen) {
		return screenVerseMap.get(screen);
	}
}
