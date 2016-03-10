package net.bible.android.control.event.window;

import net.bible.android.control.page.window.Window;

/**
 * Active window has changed and the windows are synchronized so need to change inactive window
 */
public class UpdateSecondaryWindowEvent implements WindowEvent {

	private final Window updateScreen;
	private final String html;
	private final int verseNo;
	
	public UpdateSecondaryWindowEvent(Window updateScreen, String html, int verseNo) {
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
