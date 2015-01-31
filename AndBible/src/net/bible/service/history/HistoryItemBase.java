package net.bible.service.history;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.splitscreen.Screen;
import net.bible.android.control.page.splitscreen.SplitScreenControl;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class HistoryItemBase implements HistoryItem {

	private Screen screen;
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

	public HistoryItemBase() {
		super();
		this.screen = splitScreenControl.getCurrentActiveScreen();
	}

	@Override
	public Screen getScreen() {
		return screen;
	}
}
