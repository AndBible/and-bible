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

package net.bible.service.db.bookmark;


import androidx.annotation.NonNull;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class LabelDto implements Comparable<LabelDto> {
	private Long id;
	private String name;
	private BookmarkStyle bookmarkStyle;

	public LabelDto() {
	}

	public LabelDto(Long id, String name, BookmarkStyle bookmarkStyle) {
		this.id = id;
		this.name = name;
		this.bookmarkStyle = bookmarkStyle;
	}

	@Override
	public String toString() {
		return getName();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LabelDto other = (LabelDto) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getName() {
		if(bookmarkStyle == BookmarkStyle.SPEAK) {
			return BibleApplication.getApplication().getString(R.string.speak);
		}
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public BookmarkStyle getBookmarkStyle() {
		return bookmarkStyle;
	}

	public void setBookmarkStyle(BookmarkStyle bookmarkStyle) {
		this.bookmarkStyle = bookmarkStyle;
	}

	public String getBookmarkStyleAsString() {
		if (bookmarkStyle==null) {
			return null;
		} else {
			return bookmarkStyle.name();
		}
	}

	public void setBookmarkStyleFromString(String bookmarkStyle) {
		if (StringUtils.isEmpty(bookmarkStyle)) {
			this.bookmarkStyle = null;
		} else {
			this.bookmarkStyle = BookmarkStyle.valueOf(bookmarkStyle);
		}
	}

	@Override
	public int compareTo(@NonNull LabelDto another) {
		return name.compareToIgnoreCase(another.name);
	}
}
