package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.Window;

/**
 * Correct bible page is shown but need to scroll to a different verse
 */
public class ScrollSecondaryScreenEvent implements SplitScreenEvent {

	private final Window window;
	private final int verseNo;
	
	public ScrollSecondaryScreenEvent(Window window, int verseNo) {
		this.window = window;
		this.verseNo = verseNo;
	}

	public Window getScreen() {
		return window;
	}

	public int getVerseNo() {
		return verseNo;
	}
}
