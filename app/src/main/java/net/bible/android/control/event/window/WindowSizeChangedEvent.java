package net.bible.android.control.event.window;

import java.util.Map;

import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.window.Window;

/**
 * Window size changed - often due to separator being moved
 */
public class WindowSizeChangedEvent implements WindowEvent {

	private boolean isFinished;
	private Map<Window, ChapterVerse> screenChapterVerseMap;
	
	public WindowSizeChangedEvent(boolean isFinished, Map<Window, ChapterVerse> screenChapterVerseMap) {
		this.isFinished = isFinished;
		this.screenChapterVerseMap = screenChapterVerseMap;
	}

	public boolean isFinished() {
		return isFinished;
	}

	public boolean isVerseNoSet(Window window) {
		return screenChapterVerseMap.containsKey(window);
	}

	public ChapterVerse getChapterVerse(Window window) {
		return screenChapterVerseMap.get(window);
	}
}
