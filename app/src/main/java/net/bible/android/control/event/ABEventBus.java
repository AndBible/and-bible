package net.bible.android.control.event;

import de.greenrobot.event.EventBus;

import java.util.ArrayList;

public class ABEventBus implements EventManager {

    private static EventManager defaultInstance;
    private ArrayList<Object> subscribers = new ArrayList<>();

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
            subscribers.add(subscriber);
        }
    }

    @Override
    public void register(Object subscriber) {
        EventBus.getDefault().register(subscriber);
        subscribers.add(subscriber);
    }

    @Override
    public void unregister(Object subscriber) {
        EventBus.getDefault().unregister(subscriber);
        subscribers.remove(subscriber);
    }

    /**
     * Between tests we need to clean up
     */
    @Override
    public void unregisterAll() {
        for(Object subscriber : new ArrayList<>(subscribers)) {
            unregister(subscriber);
        }
    }

    @Override
    public void post(Object event) {
        EventBus.getDefault().post(event);
    }
}
