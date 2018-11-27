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

package net.bible.service.readingplan;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.passage.Key;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class OneDaysReadingsDto implements Comparable<OneDaysReadingsDto> {
	private ReadingPlanInfoDto mReadingPlanInfoDto;
	private int mDay;
	private String mReadings;
	private List<Key> mReadingKeys;
	
	public OneDaysReadingsDto(int day, String readings, ReadingPlanInfoDto readingPlanInfo) {
		mDay = day;
		mReadings = readings;
		mReadingPlanInfoDto = readingPlanInfo;
	}
	
	@Override
	public String toString() {
		return getDayDesc();
	}

	@Override
	public int compareTo(OneDaysReadingsDto another) {
		return mDay-another.mDay;
	}
	
	public String getDayDesc() {
		return BibleApplication.getApplication().getString(R.string.rdg_plan_day, Integer.toString(mDay));
	}

	/** get a string representing the date this reading is planned for
	 */
	public String getReadingDateString() {
		String dateString = "";
		Date startDate = mReadingPlanInfoDto.getStartdate();
		if (startDate!=null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(startDate);
			cal.add(Calendar.DAY_OF_MONTH, mDay-1);
			dateString = SimpleDateFormat.getDateInstance().format(cal.getTime());
		}
		return dateString;
	}
	
	public String getReadingsDesc() {
		checkKeysGenerated();
		StringBuilder readingsBldr = new StringBuilder();
		for (int i=0; i<mReadingKeys.size(); i++) {
			if (i>0) {
				readingsBldr.append(", ");
			}
			readingsBldr.append(mReadingKeys.get(i).getName());
		}
		return readingsBldr.toString();
	}

	public Key getReadingKey(int no) {
		checkKeysGenerated();
		return mReadingKeys.get(no); 
	}
	public int getNumReadings() {
		checkKeysGenerated();
		return mReadingKeys.size(); 
	}
	
	private synchronized void checkKeysGenerated() {
		if (mReadingKeys==null) {
			List<Key> readingKeyList = new ArrayList<>();
			
			if (StringUtils.isNotEmpty(mReadings)) {
				PassageReader passageReader = new PassageReader(mReadingPlanInfoDto.getVersification());
				String[] readingArray = mReadings.split(",");
				for (String reading : readingArray) {
					//use the v11n specified in the reading plan (default is KJV) 
					readingKeyList.add(passageReader.getKey(reading));
				}
			}			
			mReadingKeys = readingKeyList;
		}
	}
	
	public ReadingPlanInfoDto getReadingPlanInfo() {
		return mReadingPlanInfoDto;
	}

	public List<Key> getReadingKeys() {
		checkKeysGenerated();
		return mReadingKeys;
	}

	public int getDay() {
		return mDay;
	}
}
