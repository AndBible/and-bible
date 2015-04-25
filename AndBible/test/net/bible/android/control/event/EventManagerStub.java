package net.bible.android.control.event;

import java.util.ArrayList;
import java.util.List;

public class EventManagerStub implements EventManager {

	private List<Object> registered = new ArrayList<>();
	
	public EventManagerStub() {
	}

	@Override
	public void register(Object subscriber) {
		registered.add(subscriber);
	}

	@Override
	public void safelyRegister(Object subscriber) {
		register(subscriber);
	}

	@Override
	public void post(Object event) {
	}

}
