package net.bible.android.control.event.window;

import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.window.Window;

/**
 * Correct bible page is shown but need to scroll to a different verse
 */
public class ScrollSecondaryWindowEvent implements WindowEvent {

	private final Window window;
	private final ChapterVerse chapterVerse;
	
	public ScrollSecondaryWindowEvent(Window window, ChapterVerse chapterVerse) {
		this.window = window;
		this.chapterVerse = chapterVerse;
	}

	public Window getWindow() {
		return window;
	}

	public ChapterVerse getChapterVerse() {
		return chapterVerse;
	}
}
