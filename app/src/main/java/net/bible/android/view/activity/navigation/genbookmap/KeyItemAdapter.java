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

package net.bible.android.view.activity.navigation.genbookmap;

import android.content.Context;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.bible.service.common.ABStringUtils;

import org.apache.commons.lang3.text.WordUtils;
import org.crosswire.jsword.passage.Key;

import java.util.List;

/**
 * Retain similar style to TwoLineListView but for single TextView on each line
 * @author denha1m
 *
 */
public class KeyItemAdapter extends ArrayAdapter<Key> {

	private int resource;

	public KeyItemAdapter(Context _context, int _resource, List<Key> _items) {
		super(_context, _resource, _items);
		resource = _resource;
	}

	@Nullable
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Key item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TextView view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TextView) inflater.inflate(resource, parent, false);
		} else {
			view = (TextView) convertView;
		}

		// Set value for the first text field
		if (view != null) {
			String key = item.getOsisID();
			// make all uppercase in Calvin's Institutes look nicer
			if (ABStringUtils.isAllUpperCaseWherePossible(key)) {
				key = WordUtils.capitalizeFully(key);
			}
			view.setText(key);
		}

		return view;
	}
}
