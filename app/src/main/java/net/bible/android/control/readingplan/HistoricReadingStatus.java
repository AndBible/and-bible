/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.readingplan;

/** return isRead' for all historical readings
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class HistoricReadingStatus extends ReadingStatus {

	public HistoricReadingStatus(String planCode, int day, int numReadings) {
		super(planCode, day, numReadings);
	}

	@Override
	public void setRead(int readingNo) {
		// do nothing - all readings are already read
	}

	@Override
	public boolean isRead(int readingNo) {
		// all readings are already read
		return true;
	}

	@Override
	public void delete() {
		// do nothing
	}

	@Override
	public void reloadStatus() {
		// do nothing
	}
	
	
}
