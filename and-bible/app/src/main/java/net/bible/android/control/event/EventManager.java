package net.bible.android.control.event;


public interface EventManager {

	void register(Object subscriber);

	void safelyRegister(Object subscriber);

	void unregister(Object subscriber);

	void unregisterAll();

	void post(Object event);
}
