/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

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
