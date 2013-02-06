package net.bible.android.control.event.splitscreen;

import java.util.EventListener;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

public interface SplitScreenEventListener extends EventListener {
	void currentSplitScreenChanged(Screen activeScreen);
	
	// active screen has changed and the screens are synchronized so need to change inactive split screen
	void updateSecondaryScreen(Screen updateScreen, String html, int verseNo);
	
	// correct bible page is shown but need to scrol to a different verse 
	void scrollSecondaryScreen(Screen screen, int verseNo);
}
