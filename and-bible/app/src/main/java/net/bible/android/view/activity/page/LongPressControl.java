package net.bible.android.view.activity.page;

/** long press normally shows a context menu but in JellyBean need to pass the long-press keystroke through to allow display of the Copy/Paste menu
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com] */
public class LongPressControl {
	private boolean ignoreNextLongPress = false;
	
	public boolean isIgnoreLongPress() {
		boolean ignore = ignoreNextLongPress;
		
		// we have now ignored 1 long press so clear the flag
		ignoreNextLongPress = false;
		return ignore; 
	}
	
	public void ignoreNextLongPress() {
		ignoreNextLongPress = true;
	}
}
