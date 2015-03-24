package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.Window;

/**
 * Active screen has changed and the screens are synchronized so need to change inactive split screen
 */
public class UpdateSecondaryScreenEvent implements SplitScreenEvent {

	private final Window updateScreen;
	private final String html;
	private final int verseNo;
	
	public UpdateSecondaryScreenEvent(Window updateScreen, String html, int verseNo) {
		this.updateScreen = updateScreen;
		this.html = html;
		this.verseNo = verseNo;
	}

	public Window getUpdateScreen() {
		return updateScreen;
	}

	public String getHtml() {
		return html;
	}

	public int getVerseNo() {
		return verseNo;
	}
}
