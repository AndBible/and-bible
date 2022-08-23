/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

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
	public void unregister(Object subscriber) {

	}

	@Override
	public void unregisterAll() {

	}

	@Override
	public void post(Object event) {
	}

}
