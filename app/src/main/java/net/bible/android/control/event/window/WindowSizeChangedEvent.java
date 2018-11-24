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

package net.bible.android.control.event.window;

import java.util.Map;

import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.window.Window;

/**
 * Window size changed - often due to separator being moved
 */
public class WindowSizeChangedEvent implements WindowEvent {

	private boolean isFinished;
	private Map<Window, ChapterVerse> screenChapterVerseMap;
	
	public WindowSizeChangedEvent(boolean isFinished, Map<Window, ChapterVerse> screenChapterVerseMap) {
		this.isFinished = isFinished;
		this.screenChapterVerseMap = screenChapterVerseMap;
	}

	public boolean isFinished() {
		return isFinished;
	}

	public boolean isVerseNoSet(Window window) {
		return screenChapterVerseMap.containsKey(window);
	}

	public ChapterVerse getChapterVerse(Window window) {
		return screenChapterVerseMap.get(window);
	}
}
