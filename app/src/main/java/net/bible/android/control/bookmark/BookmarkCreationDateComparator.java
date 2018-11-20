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

package net.bible.android.control.bookmark;

import androidx.annotation.NonNull;

import net.bible.service.db.bookmark.BookmarkDto;

import java.util.Comparator;

/**
 * Sort bookmarks by create date, most recent first
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class BookmarkCreationDateComparator implements Comparator<BookmarkDto> {

	public int compare(@NonNull BookmarkDto bookmark1, @NonNull BookmarkDto bookmark2) {
		// descending order
		return bookmark2.getCreatedOn().compareTo(bookmark1.getCreatedOn());
	}

}
