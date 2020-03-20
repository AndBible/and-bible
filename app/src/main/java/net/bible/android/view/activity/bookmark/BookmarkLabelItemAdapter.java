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

package net.bible.android.view.activity.bookmark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import net.bible.android.view.util.UiUtils;
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper;
import net.bible.service.db.bookmark.LabelDto;

import java.util.List;

/**
 * Adapter which shows highlight colour of labels
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkLabelItemAdapter extends ArrayAdapter<LabelDto> {

	private int resource;

	private BookmarkStyleAdapterHelper bookmarkStyleAdapterHelper = new BookmarkStyleAdapterHelper();

	private static final String TAG = "BookmarkLabelItemAdapter";

	public BookmarkLabelItemAdapter(Context context, int resource, List<LabelDto> items) {
		super(context, resource, items);
		this.resource = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		final LabelDto labelDto = getItem(position);

		View rowView;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(resource, parent, false);
		} else {
			rowView = convertView;
		}
		CheckedTextView nameView = (CheckedTextView) rowView;
		nameView.setText(labelDto.getName());
		if (labelDto.getBookmarkStyle() ==null) {
			nameView.setBackgroundColor(UiUtils.INSTANCE.getThemeBackgroundColour(getContext()));
		} else {
			bookmarkStyleAdapterHelper.styleView(nameView, labelDto.getBookmarkStyle(), getContext(), false, false);
		}

		return rowView;
	}
}
