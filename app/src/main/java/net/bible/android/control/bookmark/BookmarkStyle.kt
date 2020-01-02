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
package net.bible.android.control.bookmark

import android.graphics.Color

/**
 * How to represent bookmarks
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
enum class BookmarkStyle(val backgroundColor: Int) {
    YELLOW_STAR(Color.argb(0, 255, 255, 255)),
	RED_HIGHLIGHT(Color.argb((255 * 0.28).toInt(), 213, 0, 0)),
	YELLOW_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 255, 255, 0)),
	GREEN_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 0, 255, 0)),
	BLUE_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 145, 167, 255)),
	// Special hard-coded style for Speak bookmarks. This must be last one here.
    // This is removed from the style lists.
    SPEAK(Color.argb(0, 255, 255, 255));

}
