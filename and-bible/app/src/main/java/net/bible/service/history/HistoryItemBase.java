package net.bible.service.history;

import net.bible.android.control.page.window.Window;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class HistoryItemBase implements HistoryItem {

	private final Window window;
	
	public HistoryItemBase(Window window) {
		super();
		this.window = window;
	}

	@Override
	public Window getScreen() {
		return window;
	}
}
