package net.bible.android.control.event.splitscreen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

/**
 * Notify clients when SplitScreen changes 
 * 
 * @author denha1m
 * 
 */
public class SplitScreenEventManager {

	private List<SplitScreenEventListener> splitScreenEventListeners = new CopyOnWriteArrayList<SplitScreenEventListener>();

	public void addSplitScreenEventListener(SplitScreenEventListener listener) {
		splitScreenEventListeners.add(listener);
	}

	public void removeSplitScreenEventListener(SplitScreenEventListener listener) {
		splitScreenEventListeners.remove(listener);
	}

	/** Split screen has been minimized/restored/removed/added
	 */
	public void numberOfScreensChanged(Map<Screen, Integer> screenVerseMap) {
		// loop through each listener and pass on the event if needed
		for (SplitScreenEventListener listener : splitScreenEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.numberOfScreensChanged(screenVerseMap);
		}
	}

	/** Split screen has changed in size
	 */
	public void splitScreenSizeChange(boolean isFinished, Map<Screen, Integer> screenVerseMap) {
		// loop through each listener and pass on the event if needed
		for (SplitScreenEventListener listener : splitScreenEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.splitScreenSizeChange(isFinished, screenVerseMap);
		}
	}


	/** detail/verse changed
	 */
	public void splitScreenDetailChanged(Screen activeScreen) {
		// loop through each listener and pass on the event if needed
		for (SplitScreenEventListener listener : splitScreenEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.currentSplitScreenChanged(activeScreen);
		}
	}
	
	public void updateSecondaryScreen(Screen screen, String html, int verseNo) {
		// loop through each listener and pass on the event if needed
		for (SplitScreenEventListener listener : splitScreenEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.updateSecondaryScreen(screen, html, verseNo);
		}
	}
	
	public void scrollSecondaryScreen(Screen screen, int verseNo) {
		// loop through each listener and pass on the event if needed
		for (SplitScreenEventListener listener : splitScreenEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.scrollSecondaryScreen(screen, verseNo);
		}
	}
}
