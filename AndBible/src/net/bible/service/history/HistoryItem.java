package net.bible.service.history;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

/**
 * An item in the History List
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public interface HistoryItem {

	public CharSequence getDescription();
	
	public Screen getScreen();
	
	// do back to the state at this point
	public abstract void revertTo();

}