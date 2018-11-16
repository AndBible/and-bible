package net.bible.android.control.event.window;

import java.util.Map;

import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.window.Window;

/**
 * 	Window has been minimized/restored/closed/added
 */
public class NumberOfWindowsChangedEvent implements WindowEvent {
	
	private Map<Window, ChapterVerse> screenChapterVerseMap;

	public NumberOfWindowsChangedEvent(Map<Window, ChapterVerse> screenChapterVerseMap) {
		this.screenChapterVerseMap = screenChapterVerseMap;
	}

	public boolean isVerseNoSet(Window window) {
		return screenChapterVerseMap.containsKey(window);
	}

	public ChapterVerse getChapterVerse(Window window) {
		return screenChapterVerseMap.get(window);
	}
}
