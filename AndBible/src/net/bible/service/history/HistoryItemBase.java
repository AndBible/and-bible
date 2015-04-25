package net.bible.service.history;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.splitscreen.Window;
import net.bible.android.control.page.splitscreen.WindowControl;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class HistoryItemBase implements HistoryItem {

	private Window window;
	
	private static WindowControl windowControl = ControlFactory.getInstance().getSplitScreenControl();

	public HistoryItemBase() {
		super();
		this.window = windowControl.getCurrentActiveWindow();
	}

	@Override
	public Window getScreen() {
		return window;
	}
}
