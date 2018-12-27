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

package net.bible.service.history;

import android.util.Log;

import net.bible.android.control.page.window.Window;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

/**
 * A normal item in the history list that relates to a document being shown in the main activity view 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class KeyHistoryItem extends HistoryItemBase {
	private Book document;
	private Key key;
	private float yOffsetRatio;

	private static final String TAG = "KeyHistoryItem"; 

	public KeyHistoryItem(Book doc, Key verse, float yOffsetRatio, Window window) {
		super(window);
		this.document = doc;
		this.key = verse;
		this.yOffsetRatio = yOffsetRatio;
	}
	
	/* (non-Javadoc)
	 * @see net.bible.service.history.HistoryItem#revertTo()
	 */
	@Override
	public void revertTo() {
		getScreen().getPageManager().setCurrentDocumentAndKeyAndOffset(document, key, yOffsetRatio);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder();
		try {
			String verseDesc = CommonUtils.getKeyDescription(key);
			desc.append(document.getAbbreviation()).append(" ").append(verseDesc);
		} catch (Exception e) {
			Log.e(TAG, "Error getting description", e);
		}
		return desc.toString();
	}

	public Key getKey() {
		return key;
	}

	@Override
	public String toString() {
		return getDescription();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((document == null) ? 0 : document.getInitials().hashCode());
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		return result;
	}

	//TODO use Book.equals and Key.equals in the below
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		KeyHistoryItem other = (KeyHistoryItem) obj;
		if (document == null) {
			if (other.document != null)
				return false;
		} else if (document.getInitials() == null) {
			if (other.document.getInitials() != null)
				return false;
		} else if (!document.getInitials().equals(other.document.getInitials()))
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}
}
