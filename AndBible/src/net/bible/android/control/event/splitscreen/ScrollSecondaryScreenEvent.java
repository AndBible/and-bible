package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.Screen;

/**
 * Correct bible page is shown but need to scroll to a different verse
 */
public class ScrollSecondaryScreenEvent implements SplitScreenEvent {

	private final Screen screen;
	private final int verseNo;
	
	public ScrollSecondaryScreenEvent(Screen screen, int verseNo) {
		this.screen = screen;
		this.verseNo = verseNo;
	}

	public Screen getScreen() {
		return screen;
	}

	public int getVerseNo() {
		return verseNo;
	}
}
