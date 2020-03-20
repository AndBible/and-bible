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

package net.bible.android.view.activity.page;

import android.util.Log;
import android.view.KeyEvent;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;

import javax.inject.Inject;

/** KeyEvent.KEYCODE_DPAD_LEFT was being swallowed by the BibleView after scrolling down (it gained focus)
 * so this class implements common key handling both for BibleView and MainBibleActivity
 *   
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class BibleKeyHandler {
	
	// prevent too may scroll events causing multi-page changes
	private long lastHandledDpadEventTime = 0;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final String TAG = "BibleKeyHandler";

	@Inject
	public BibleKeyHandler(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
	
	/** handle DPAD keys
	 */
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			Log.d(TAG, "D-Pad");
			// prevent too may scroll events causing multi-page changes
			if (event.getEventTime()-lastHandledDpadEventTime>1000) {
				if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) {
					activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().next();
				} else {
					activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().previous();
				}
				lastHandledDpadEventTime = event.getEventTime();
				return true;
			}
		}
		return false;
	}


}
