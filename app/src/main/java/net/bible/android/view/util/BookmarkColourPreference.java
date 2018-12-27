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

package net.bible.android.view.util;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.widget.ListAdapter;
import android.widget.ListView;

import net.bible.android.control.bookmark.BookmarkStyle;

/**
 * Allow selection of default Bookmark colour preference.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkColourPreference extends ListPreference {

	public BookmarkColourPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialise();
	}

	public BookmarkColourPreference(Context context) {
		super(context);
		initialise();
	}

	private void initialise() {
		final int numValues = BookmarkStyle.values().length - 1; // Do not count Speak bookmark style
		CharSequence[] styles = new CharSequence[numValues];
		for (int i=0; i<numValues; i++) {
			BookmarkStyle bookmarkStyle = BookmarkStyle.values()[i];
			styles[i] = bookmarkStyle.name();
		}
		setEntries(styles);
		setEntryValues(styles);
		setDefaultValue(styles[0]);
	}

	@Override
	protected void showDialog(Bundle state) {
		super.showDialog(state);
		AlertDialog dialog = (AlertDialog) getDialog();
		ListView listView = dialog.getListView();
		ListAdapter adapter = listView.getAdapter();
		final BookmarkColourListPrefWrapperAdapter fontTypeAdapter = createWrapperAdapter(adapter);

		// Adjust the selection because resetting the adapter loses the selection.
		int selectedPosition = findIndexOfValue(getValue());
		listView.setAdapter(fontTypeAdapter);
		if (selectedPosition != -1) {
			listView.setItemChecked(selectedPosition, true);
			listView.setSelection(selectedPosition);
		}
	}

	protected BookmarkColourListPrefWrapperAdapter createWrapperAdapter(ListAdapter origAdapter) {
		return new BookmarkColourListPrefWrapperAdapter(getContext(), origAdapter);
	}
}
