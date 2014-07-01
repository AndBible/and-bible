package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

/**
 * Active screen has changed and the screens are synchronized so need to change inactive split screen
 */
public class UpdateSecondaryScreenEvent implements SplitScreenEvent {

	private final Screen updateScreen;
	private final String html;
	private final int verseNo;
	
	public UpdateSecondaryScreenEvent(Screen updateScreen, String html, int verseNo) {
		this.updateScreen = updateScreen;
		this.html = html;
		this.verseNo = verseNo;
	}

	public Screen getUpdateScreen() {
		return updateScreen;
	}

	public String getHtml() {
		return html;
	}

	public int getVerseNo() {
		return verseNo;
	}
}
