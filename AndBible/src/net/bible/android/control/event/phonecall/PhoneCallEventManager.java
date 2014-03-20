package net.bible.android.control.event.phonecall;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify clients when app goes to/from background 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PhoneCallEventManager {

	private List<PhoneCallListener> phoneCallEventListeners = new CopyOnWriteArrayList<PhoneCallListener>();

	private static final PhoneCallEventManager phoneCallEventManager = new PhoneCallEventManager();

	public static PhoneCallEventManager getInstance() {
		return phoneCallEventManager;
	}

	public void addPhoneCallListener(PhoneCallListener listener) {
		// don't start monitoring until required
		PhoneCallMonitor.ensureMonitoringStarted();
		
		phoneCallEventListeners.add(listener);
	}

	public void removePhoneCallListener(PhoneCallListener listener) {
		phoneCallEventListeners.remove(listener);
	}

	public void phoneCallStarted() {
		// loop through each listener and pass on the event if needed
		for (PhoneCallListener listener : phoneCallEventListeners) {
			// pass the event to the listeners event dispatch method
			listener.phoneCallStarted();
		}
	}
}
