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

package net.bible.android.view.activity.footnoteandref;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

import net.bible.android.control.footnoteandref.NoteDetailCreator;
import net.bible.service.format.Note;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ItemAdapter extends ArrayAdapter<Note> {

	private int resource;
	private final NoteDetailCreator noteDetailCreator;

	public ItemAdapter(Context _context, int _resource, List<Note> _items, NoteDetailCreator noteDetailCreator) {
		super(_context, _resource, _items);
		resource = _resource;
		this.noteDetailCreator = noteDetailCreator;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Note item = getItem(position);

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
			String summary = item.getSummary();
			view.getText1().setText(Html.fromHtml(summary));
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String detail = noteDetailCreator.getDetail(item);
			view.getText2().setText(Html.fromHtml(detail));
		}

		return view;
	}
}
