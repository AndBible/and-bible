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

import java.util.BitSet;

import net.bible.service.common.CommonUtils;
import android.content.SharedPreferences;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ReadingStatus {

	private String planCode;
	private int day;
	private int numReadings;
	
	// there won't be any more than 10 readings per day in any plan
	private BitSet status = new BitSet(4);
	private static final char ONE = '1';
	private static final char ZERO = '0';

	public ReadingStatus(String planCode, int day, int numReadings) {
		super();
		this.planCode = planCode;
		this.day = day;
		this.numReadings = numReadings;
		reloadStatus();
	}

	public void setRead(int readingNo) {
		status.set(readingNo);
		saveStatus();
	}
	
	public boolean isRead(int readingNo) {
		return status.get(readingNo);
	}
	public void setAllRead() {
		for (int i=0; i<numReadings; i++) {
			setRead(i);
		}
		saveStatus();
	}
	public boolean isAllRead() {
		for (int i=0; i<numReadings; i++) {
			if (!isRead(i)) {
				return false;
			}
		}
		return true;
	}

	/** do not leave prefs around for historic days
	 */
	public void delete() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		if (prefs.contains(getPrefsKey())) {
			prefs.edit()
				.remove(getPrefsKey())
				.commit();
		}
	}
	
	/** read status from prefs string
	 */
	public void reloadStatus() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		String gotStatus = prefs.getString(getPrefsKey(), "");
		for (int i=0; i<gotStatus.length(); i++) {
			if (gotStatus.charAt(i)==ONE) {
				status.set(i);
			} else {
				status.clear(i);
			}
		}
	}

	/** serialize read status to prefs in a string
	 */
	private void saveStatus() {
		StringBuffer strStatus = new StringBuffer();
		for (int i=0; i<status.length(); i++) {
			if (status.get(i)) {
				strStatus.append(ONE);
			} else {
				strStatus.append(ZERO);
			}
		}
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		prefs.edit()
			.putString(getPrefsKey(), strStatus.toString())
			.commit();
	}
	
	protected String getPlanCode() {
		return planCode;
	}

	protected int getDay() {
		return day;
	}

	private String getPrefsKey() {
		return planCode+"_"+day;
	}
	
}
