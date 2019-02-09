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

package net.bible.android.view.activity.base;

import android.view.View;

import net.bible.android.control.page.ChapterVerse;

/**
 * Base class for boble and My Note document views
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public interface DocumentView {

	void show(String html, ChapterVerse chapterVerse, float jumpToYOffsetRatio);

	void applyPreferenceSettings();

	/** may need updating depending on environmental brightness
	 */
	void changeBackgroundColour();
	
	// allow stop/start of autoscroll
	void onScreenTurnedOn();
	void onScreenTurnedOff();
	
	boolean pageDown(boolean toBottom);
	
	/** prevent swipe right if the user is scrolling the page right */
	boolean isPageNextOkay();
	
	/** prevent swipe left if the user is scrolling the page left */
	boolean isPagePreviousOkay();
	
    float getCurrentPosition();
    
    /** same as this but of type View */
    View asView();
}
