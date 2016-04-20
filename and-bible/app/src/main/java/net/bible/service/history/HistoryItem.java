package net.bible.service.history;

import net.bible.android.control.page.window.Window;

/**
 * An item in the History List
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public interface HistoryItem {

	public CharSequence getDescription();
	
	public Window getScreen();
	
	// do back to the state at this point
	public abstract void revertTo();

	// if this Item came from SharedPreferences, we shouldn't go to it at Back key pressed
	public abstract boolean isFromPersistentHistory();

}