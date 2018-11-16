package net.bible.android.control.event.apptobackground;

/** Event raised when teh application moves to the background e.g. when phone rings and ringer comes in front of this app
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class AppToBackgroundEvent {
	public enum Position {
		FOREGROUND,
		BACKGROUND
	}
	
	private Position newPosition;

	public AppToBackgroundEvent(Position newPosition) {
		super();
		this.newPosition = newPosition;
	}

	public Position getNewPosition() {
		return newPosition;
	}
	
	public boolean isMovedToBackground() {
		return newPosition == Position.BACKGROUND;
	}
}
