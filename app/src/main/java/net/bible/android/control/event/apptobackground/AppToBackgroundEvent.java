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

package net.bible.android.control.event.apptobackground;

/** Event raised when teh application moves to the background e.g. when phone rings and ringer comes in front of this app
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class AppToBackgroundEvent {
	public enum Position {
		FOREGROUND,
		BACKGROUND
	}
	
	private Position newPosition;

	public AppToBackgroundEvent(Position newPosition) {
		super();
		this.newPosition = newPosition;
	}

	public Position getNewPosition() {
		return newPosition;
	}
	
	public boolean isMovedToBackground() {
		return newPosition == Position.BACKGROUND;
	}
}
