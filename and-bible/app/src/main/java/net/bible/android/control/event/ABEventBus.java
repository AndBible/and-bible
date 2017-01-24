package net.bible.android.control.event;

import de.greenrobot.event.EventBus;

public class ABEventBus implements EventManager {

	private static EventManager defaultInstance;

    public static EventManager getDefault() {
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
	@Override
	public void safelyRegister(Object subscriber) {
		EventBus defaulteventBus = EventBus.getDefault();
		if (!defaulteventBus.isRegistered(subscriber)) {
			defaulteventBus.register(subscriber);
		}
	}

	@Override
	public void register(Object subscriber) {
		EventBus.getDefault().register(subscriber);
	}

	@Override
	public void post(Object event) {
		EventBus.getDefault().post(event);
	}
}
