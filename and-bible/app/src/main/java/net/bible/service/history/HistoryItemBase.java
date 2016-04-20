package net.bible.service.history;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class HistoryItemBase implements HistoryItem {

	private Window window;
	
	private static WindowControl windowControl = ControlFactory.getInstance().getWindowControl();

	public HistoryItemBase() {
		super();
		this.window = windowControl.getActiveWindow();
	}

	@Override
	public Window getScreen() {
		return window;
	}

	// only KeyHistoryItem can be serialized/deserialized to SharedPrefs, so return false for all other childs
	@Override
	public boolean isFromPersistentHistory() {
		return false;
	}
}
