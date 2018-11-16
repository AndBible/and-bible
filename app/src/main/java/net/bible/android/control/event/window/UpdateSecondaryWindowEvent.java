package net.bible.android.control.event.window;

import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.window.Window;

/**
 * Active window has changed and the windows are synchronized so need to change inactive window
 */
public class UpdateSecondaryWindowEvent implements WindowEvent {

	private final Window updateScreen;
	private final String html;
	private final ChapterVerse chapterVerse;
	
	public UpdateSecondaryWindowEvent(Window updateScreen, String html, ChapterVerse chapterVerse) {
		this.updateScreen = updateScreen;
		this.html = html;
		this.chapterVerse = chapterVerse;
	}

	public Window getUpdateScreen() {
		return updateScreen;
	}

	public String getHtml() {
		return html;
	}

	public ChapterVerse getChapterVerse() {
		return chapterVerse;
	}
}
