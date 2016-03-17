package net.bible.service.device.speak.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Notify clients when speak state changes between speaking, paused, quiet
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakEventManager {

	private List<SpeakEventListener> speakEventListeners = new CopyOnWriteArrayList<SpeakEventListener>();

	private static final SpeakEventManager speakEventManager = new SpeakEventManager();
	
	private SpeakEvent lastEvent;
	
	public static SpeakEventManager getInstance() {
		return speakEventManager;
	}
	
	public void addSpeakEventListener(SpeakEventListener listener) 
	{
	     speakEventListeners.add(listener);
	     if (lastEvent!=null) {
	    	 // refire last speak event in case state is not default when listener registers or was unregistered when state changed
	    	 listener.speakStateChange(lastEvent);
	     }
	}

	public void removeSpeakEventListener(SpeakEventListener listener) 
	{
	     speakEventListeners.remove(listener);
	}

	public void speakStateChanged(SpeakEvent speakEvent) {
		// loop through each listener and pass on the event if needed
		for (SpeakEventListener listener : speakEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.speakStateChange(speakEvent);
		}

		lastEvent = speakEvent;
	}
	
}
