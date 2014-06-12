package net.bible.android.control.event;

import de.greenrobot.event.EventBus;

public class ABEventBus {

	private static ABEventBus defaultInstance;
	
    public static ABEventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (ABEventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new ABEventBus();
                }
            }
        }
        return defaultInstance;
    }
	
	private ABEventBus() {
	}

	/**
	 * Check not registered before registering to avoid exception
	 */
	public void safelyRegister(Object subscriber) {
		EventBus defaulteventBus = EventBus.getDefault();
		if (!defaulteventBus.isRegistered(subscriber)) {
			defaulteventBus.register(subscriber);
		}
	}
}
