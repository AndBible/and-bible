package net.bible.android.control.event.passage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify clients when passage changes 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PassageEventManager {

	private List<PassageEventListener> passageEventListeners = new CopyOnWriteArrayList<PassageEventListener>();

	private static final PassageEventManager passageEventManager = new PassageEventManager();

	public static PassageEventManager getInstance() {
		return passageEventManager;
	}

	public void addPassageEventListener(PassageEventListener listener) {
		passageEventListeners.add(listener);
	}

	public void removePassageEventListener(PassageEventListener listener) {
		passageEventListeners.remove(listener);
	}

	/** detail/verse changed
	 */
	public void passageDetailChanged() {
		PassageEvent event = new PassageEvent();
		// loop through each listener and pass on the event if needed
		for (PassageEventListener listener : passageEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.pageDetailChange(event);
		}
	}
}
