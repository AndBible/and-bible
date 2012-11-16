package net.bible.android.control.event.passage;

import org.crosswire.common.util.EventListenerList;

/**
 * Notify clients when passage changes 
 * 
 * @author denha1m
 * 
 */
public class PassageEventManager {

	private EventListenerList passageEventListeners = new EventListenerList();

	private static final PassageEventManager passageEventManager = new PassageEventManager();

	public static PassageEventManager getInstance() {
		return passageEventManager;
	}

	public void addPassageEventListener(PassageEventListener listener) {
		passageEventListeners.add(PassageEventListener.class, listener);
	}

	public void removePassageEventListener(PassageEventListener listener) {
		passageEventListeners.remove(PassageEventListener.class, listener);
	}

	/** detail/verse changed
	 */
	public void passageDetailChanged() {
		PassageEvent event = new PassageEvent();
		Object[] listeners = passageEventListeners.getListenerList();
		// loop through each listener and pass on the event if needed
		int numListeners = listeners.length;
		for (int i = 0; i < numListeners; i += 2) {
			if (listeners[i] == PassageEventListener.class) {
				// pass the event to the listeners event dispatch method
				((PassageEventListener) listeners[i + 1]).pageDetailChange(event);
			}
		}
	}

}
