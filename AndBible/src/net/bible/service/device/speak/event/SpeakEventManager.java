package net.bible.service.device.speak.event;

import org.crosswire.common.util.EventListenerList;

/** Notify clients when speak state changes between speaking, paused, quiet
 * 
 * @author denha1m
 *
 */
public class SpeakEventManager {

	private EventListenerList speakEventListeners = new EventListenerList();

	private static final SpeakEventManager speakEventManager = new SpeakEventManager();
	
	private SpeakEvent lastEvent;
	
	public static SpeakEventManager getInstance() {
		return speakEventManager;
	}
	
	public void addSpeakEventListener(SpeakEventListener listener) 
	{
	     speakEventListeners.add(SpeakEventListener.class, listener);
	     if (lastEvent!=null) {
	    	 // refire last speak event in case state is not default when listener registers or was unregistered when state changed
	    	 listener.speakStateChange(lastEvent);
	     }
	}

	public void removeSpeakEventListener(SpeakEventListener listener) 
	{
	     speakEventListeners.remove(SpeakEventListener.class, listener);
	}

	public void speakStateChanged(SpeakEvent speakEvent) {
	     Object[] listeners = speakEventListeners.getListenerList();
	     // loop through each listener and pass on the event if needed
	     int numListeners = listeners.length;
	     for (int i = 0; i<numListeners; i+=2) 
	     {
	          if (listeners[i]==SpeakEventListener.class) 
	          {
	               // pass the event to the listeners event dispatch method
	                ((SpeakEventListener)listeners[i+1]).speakStateChange(speakEvent);
	          }            
	     }
	     
	     lastEvent = speakEvent;
	}
	
}
