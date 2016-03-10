package net.bible.android.control.event;


public interface EventManager {

	void register(Object subscriber);

	void safelyRegister(Object subscriber);
	
	void post(Object event);
}
