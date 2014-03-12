package net.bible.android.control.event.apptobackground;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify clients when app goes to/from background 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AppToBackgroundEventManager {

	private List<AppToBackgroundListener> appToBackgroundEventListeners = new CopyOnWriteArrayList<AppToBackgroundListener>();

	private static final AppToBackgroundEventManager appToBackgroundEventManager = new AppToBackgroundEventManager();

	public static AppToBackgroundEventManager getInstance() {
		return appToBackgroundEventManager;
	}

	public void addAppToBackgroundListener(AppToBackgroundListener listener) {
		appToBackgroundEventListeners.add(listener);
	}

	public void removeAppToBackgroundListener(AppToBackgroundListener listener) {
		appToBackgroundEventListeners.remove(listener);
	}

	public void appNowInBackground(boolean movedToBackground) {
		AppToBackgroundEvent event = new AppToBackgroundEvent();
		// loop through each listener and pass on the event if needed
		for (AppToBackgroundListener listener : appToBackgroundEventListeners) {
			// pass the event to the listeners event dispatch method
			if (movedToBackground) {
				listener.applicationNowInBackground(event);
			} else {
				listener.applicationReturnedFromBackground(event);
			}
		}
	}
}
