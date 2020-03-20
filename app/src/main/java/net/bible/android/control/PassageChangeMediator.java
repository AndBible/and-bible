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

package net.bible.android.control;

import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent;
import net.bible.android.control.event.passage.CurrentVerseChangedEvent;
import net.bible.android.control.event.passage.PassageChangeStartedEvent;
import net.bible.android.control.event.passage.PassageChangedEvent;
import net.bible.android.control.event.passage.PreBeforeCurrentPageChangeEvent;
import net.bible.android.control.page.window.Window;
import android.util.Log;

/** when a bible passage is changed there are lots o things to update and they should be done in a helpful order
 * This helps to control screen updates after a passage change
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class PassageChangeMediator {

	private BibleContentManager mBibleContentManager;

	private static final String TAG = "PassageChangeMediator";
	
	private static final PassageChangeMediator singleton = new PassageChangeMediator();
	
	public static final PassageChangeMediator getInstance() {
		return singleton;
	}

	public void onBeforeCurrentPageChanged() {
		onBeforeCurrentPageChanged(true);
	}

	/** first time we know a page or doc will imminently change
	 */
	public void onBeforeCurrentPageChanged(boolean updateHistory) {
		ABEventBus.getDefault().post(new PreBeforeCurrentPageChangeEvent());
		ABEventBus.getDefault().post(new BeforeCurrentPageChangeEvent(updateHistory));
	}
	
	/** the document has changed so ask the view to refresh itself
	 */
	public void onCurrentPageChanged(Window window) {
		if (mBibleContentManager!=null) {
			mBibleContentManager.updateText(window);
		} else {
			Log.w(TAG, "BibleContentManager not yet registered");
		}
		ABEventBus.getDefault().post(new CurrentVerseChangedEvent(window));
	}

	public void onCurrentPageChanged() {
		this.onCurrentPageChanged(null);
	}

	/** this is triggered on scroll
	 */
	public void onCurrentVerseChanged() {
		ABEventBus.getDefault().post(new CurrentVerseChangedEvent());
	}

	/** The thread which fetches the new page html has started
	 */
	public void contentChangeStarted() {
		ABEventBus.getDefault().post(new PassageChangeStartedEvent());
	}
	/** finished fetching html so should hide hourglass
	 */
	public void contentChangeFinished() {
		ABEventBus.getDefault().post(new PassageChangedEvent());
	}
	
	public void setBibleContentManager(BibleContentManager bibleContentManager) {
		this.mBibleContentManager = bibleContentManager;
	}
}
