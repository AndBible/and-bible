package net.bible.android.control.event.splitscreen;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

import org.crosswire.common.util.EventListenerList;

/**
 * Notify clients when SplitScreen changes 
 * 
 * @author denha1m
 * 
 */
public class SplitScreenEventManager {

	private EventListenerList splitScreenEventListeners = new EventListenerList();

	public void addSplitScreenEventListener(SplitScreenEventListener listener) {
		splitScreenEventListeners.add(SplitScreenEventListener.class, listener);
	}

	public void removeSplitScreenEventListener(SplitScreenEventListener listener) {
		splitScreenEventListeners.remove(SplitScreenEventListener.class, listener);
	}

	/** detail/verse changed
	 */
	public void splitScreenDetailChanged(Screen screen) {
		SplitScreenEvent event = new SplitScreenEvent(screen);
		Object[] listeners = splitScreenEventListeners.getListenerList();
		// loop through each listener and pass on the event if needed
		int numListeners = listeners.length;
		for (int i = 0; i < numListeners; i += 2) {
			if (listeners[i] == SplitScreenEventListener.class) {
				// pass the event to the listeners event dispatch method
				((SplitScreenEventListener) listeners[i + 1]).currentSplitScreenChanged(event);
			}
		}
	}
	
	public void updateSecondaryScreen(Screen screen, String html, int verseNo) {
		Object[] listeners = splitScreenEventListeners.getListenerList();
		// loop through each listener and pass on the event if needed
		int numListeners = listeners.length;
		for (int i = 0; i < numListeners; i += 2) {
			if (listeners[i] == SplitScreenEventListener.class) {
				// pass the event to the listeners event dispatch method
				((SplitScreenEventListener) listeners[i + 1]).updateSecondaryScreen(screen, html, verseNo);
			}
		}
	}
	
	public void scrollSecondaryScreen(Screen screen, int verseNo) {
		Object[] listeners = splitScreenEventListeners.getListenerList();
		// loop through each listener and pass on the event if needed
		int numListeners = listeners.length;
		for (int i = 0; i < numListeners; i += 2) {
			if (listeners[i] == SplitScreenEventListener.class) {
				// pass the event to the listeners event dispatch method
				((SplitScreenEventListener) listeners[i + 1]).scrollSecondaryScreen(screen, verseNo);
			}
		}
	}

}
