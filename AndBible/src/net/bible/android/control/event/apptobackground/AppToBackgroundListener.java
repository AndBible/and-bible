package net.bible.android.control.event.apptobackground;

import java.util.EventListener;

/**
 * Interface called when App is no longer in foreground, or returns to foreground
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface AppToBackgroundListener extends EventListener {
	void applicationNowInBackground(AppToBackgroundEvent e);
	void applicationReturnedFromBackground(AppToBackgroundEvent e);
}
