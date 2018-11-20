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

package net.bible.android.view.activity.page;

import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BibleViewGestureListener extends SimpleOnGestureListener {

	private BibleView bibleView;

	private static final String TAG = "BibleGestureListener";

	public BibleViewGestureListener(BibleView bibleView) {
		super();
		this.bibleView = bibleView;
	}

//	/** WebView does not handle long presses automatically via onCreateContextMenu so do it here
//	 */
//	@Override
//	public void onLongPress(MotionEvent e) {
//		Log.d(TAG, "onLongPress");
//
//		bibleView.onLongPress(e.getX(), e.getY());
//	}
}
