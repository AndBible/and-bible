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

import net.bible.android.control.page.ChapterVerse;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Automatically find current verse at top of display to aid quick movement to Commentary.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class VerseCalculator {

	private SortedMap<Integer, ChapterVerse> verseByOffset = new TreeMap<>();

	// going to a verse pushes the offset a couple of pixels past the verse position on large screens i.e. going to Judg 5:11 will show Judg 5:12
	private static final int SLACK_FOR_JUMP_TO_VERSE = 5;

	public void init() {
		verseByOffset = new TreeMap<>();
	}
	
	/**
	 * when a page is displayed js calls this function to recored the position of all verses to enable current verse calculation
	 */
	public void registerVersePosition(ChapterVerse chapterVerse, int offset) {
		if (!verseByOffset.containsKey(offset)) {
			verseByOffset.put(offset, chapterVerse);
		}
	}
	
	/** compare scrollOffset to the verseByOffset to find which verse is at the top of the screen
	 * 
	 * @param scrollOffset    distance from the top of the screen.
	 * @return verse number
	 */
	public ChapterVerse calculateCurrentVerse(int scrollOffset) {
		int adjustedScrollOffset = scrollOffset - SLACK_FOR_JUMP_TO_VERSE;

		for (Map.Entry<Integer, ChapterVerse> entry : verseByOffset.entrySet()) {
			if (entry.getKey() > adjustedScrollOffset) {
				return entry.getValue();
			}
		}
		// maybe scrolled off bottom
		return verseByOffset.get(verseByOffset.lastKey());
	}
}
