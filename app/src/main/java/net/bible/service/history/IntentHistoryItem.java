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

package net.bible.service.history;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import net.bible.android.control.page.window.Window;
import net.bible.android.view.activity.base.CurrentActivityHolder;

/**
 * Any item in the History list that is not related to the main bible activity view e.g. search results etc
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class IntentHistoryItem extends HistoryItemBase {

	private CharSequence description;
	private Intent intent;
	
	private static final String TAG = "IntentHistoryItem"; 
	
	public IntentHistoryItem(CharSequence description, Intent intent, Window window) {
		super(window);
		this.description = description;
		this.intent = intent;
		
		// prevent re-add of intent to history if reverted to
//		intent.putExtra(HISTORY_INTENT, true);
	}

	@Override
	public boolean equals(Object o) {
		if (o==null || !(o instanceof IntentHistoryItem)) {
			return false;
		}
		if (o==this) {
			return true;
		}
		
		IntentHistoryItem oihs = (IntentHistoryItem)o;
		// assumes intent exists
		return intent.equals(oihs.intent);
	}

	@Override
	public CharSequence getDescription() {
		return description;
	}

	@Override
	public void revertTo() {
		Log.d(TAG, "Revert to history item:"+description);
		// need to get current activity and call startActivity on that 
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();

		// start activity chosen from activity
		currentActivity.startActivity(intent);
	}
}
