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

package net.bible.android.view.activity.mynote;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.bible.android.activity.R;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.view.activity.base.ListActionModeHelper;
import net.bible.android.view.util.widget.TwoLineListItem;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.mynote.MyNoteDto;

import java.util.List;

/**
 * Display a single Note in a list row
 * 
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteItemAdapter extends ArrayAdapter<MyNoteDto> {
	private int resource;
	private MyNoteControl myNoteControl;

	private final ListActionModeHelper.ActionModeActivity actionModeActivity;

	private static int ACTIVATED_COLOUR = CommonUtils.INSTANCE.getResourceColor(R.color.list_item_activated);

	private static final String TAG = "UserNoteItemAdapter";

	public MyNoteItemAdapter(Context _context, int _resource, List<MyNoteDto> _items, ListActionModeHelper.ActionModeActivity actionModeActivity, MyNoteControl myNoteControl) {
		super(_context, _resource, _items);
		resource = _resource;
		this.myNoteControl = myNoteControl;
		this.actionModeActivity = actionModeActivity;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		MyNoteDto item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TwoLineListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TwoLineListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (TwoLineListItem) convertView;
		}

		// Set value for the first text field
		if (view.getText1() != null) {
			String key = myNoteControl.getMyNoteVerseKey(item);
			view.getText1().setText(key);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			try {
				String noteText = myNoteControl.getMyNoteText(item, true);
				view.getText2().setText(noteText);
			} catch (Exception e) {
				Log.e(TAG, "Error loading label verse text", e);
				view.getText2().setText("");
			}
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			if (actionModeActivity.isItemChecked(position)) {
				view.setBackgroundColor(ACTIVATED_COLOUR);
			} else {
				view.setBackgroundColor(Color.TRANSPARENT);
			}
		}

		return view;
	}
}
