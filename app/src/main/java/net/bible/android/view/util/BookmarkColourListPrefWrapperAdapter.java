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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper;
import net.bible.android.view.util.widget.ListPrefWrapperAdapter;
import net.bible.service.common.CommonUtils;

/**
 * Set each list view item to represent background colour od icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkColourListPrefWrapperAdapter extends ListPrefWrapperAdapter {

	private Context context;

	private String sampleText = CommonUtils.INSTANCE.getResourceString(R.string.prefs_text_size_sample_text);

	private BookmarkStyleAdapterHelper bookmarkStyleAdapterHelper;

    public BookmarkColourListPrefWrapperAdapter(Context context, ListAdapter origAdapter) {
		super(origAdapter);
		this.context = context;
		this.bookmarkStyleAdapterHelper = new BookmarkStyleAdapterHelper();
    }

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView view = (TextView)super.getView(position, convertView, parent);
		final BookmarkStyle bookmarkStyle = BookmarkStyle.values()[position];

		bookmarkStyleAdapterHelper.styleView(view, bookmarkStyle, context, true, true);
		return view;
	}
}
