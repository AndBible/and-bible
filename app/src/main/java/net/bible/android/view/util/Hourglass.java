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

package net.bible.android.view.util;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.app.Activity;
import android.app.ProgressDialog;
import android.util.Log;

/** Helper class to show HourGlass
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Hourglass {
	
	private ProgressDialog hourglass;
	
	private static final String TAG = "HourGlass";
	
	public Hourglass() {
		super();
	}

	public void show() {
		final Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (activity!=null) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					hourglass = new ProgressDialog(activity);
					hourglass.setMessage(BibleApplication.Companion.getApplication().getText(R.string.please_wait));
					hourglass.setIndeterminate(true);
					hourglass.setCancelable(false);
					hourglass.show();
				}
			});
		}
	}
	
	public void dismiss() {
		try {
			if (hourglass!=null) {
				final Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
				if (activity!=null) {
					activity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							hourglass.dismiss();
							hourglass = null;
						}
					});
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error dismissing hourglass", e);
		}
	}

	public ProgressDialog getHourglass() {
		return hourglass;
	}
}
