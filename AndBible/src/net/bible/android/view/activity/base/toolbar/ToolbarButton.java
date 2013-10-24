package net.bible.android.view.activity.base.toolbar;

/**
 * Interface for toolbar buttons
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface ToolbarButton {
	public void update();
	
	public void setEnoughRoomInToolbar(boolean isRoom);
	public void setNarrow(boolean isNarrow);
	
	// is the current state suitable to show this button
	public boolean canShow();
	
	public int getPriority();
}
