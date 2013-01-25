package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

/** Event raised when the current split screen changes
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SplitScreenEvent {
	private Screen currentActiveScreen = Screen.SCREEN_1;

	public SplitScreenEvent(Screen currentActiveScreen) {
		this.currentActiveScreen = currentActiveScreen;
	}

	public Screen getCurrentActiveScreen() {
		return currentActiveScreen;
	}
}
