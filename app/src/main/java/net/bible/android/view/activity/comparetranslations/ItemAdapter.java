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

package net.bible.android.view.activity.comparetranslations;

import java.util.List;

import net.bible.android.control.comparetranslations.TranslationDto;

import org.crosswire.jsword.book.Book;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.TwoLineListItem;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ItemAdapter extends ArrayAdapter<TranslationDto> {

	private int resource;
	
	public ItemAdapter(Context _context, int _resource, List<TranslationDto> _items) {
		super(_context, _resource, _items);
		resource = _resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		TranslationDto translationDto = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TwoLineListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TwoLineListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (TwoLineListItem) convertView;
		}

		// Set value for the first text field
		TextView textView1 = view.getText1();
		if (textView1!=null) {
			Book book = translationDto.getBook();
			textView1.setText(book.getAbbreviation());
		}

		// set value for the second text field
		TextView textView2 = view.getText2();
		if (textView2!=null) {
			// but first, this book may require a custom font to display it
			if (translationDto.getCustomFontFile()!=null) {
				Typeface typeFace = Typeface.createFromFile(translationDto.getCustomFontFile());
				textView2.setTypeface(typeFace, Typeface.NORMAL);
			} else {
				// reset typeface in case this TwoLineListItemView is being reused
				textView2.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
			}
			String verseText = translationDto.getText();
			textView2.setText(verseText);
		}
		
		return view;
	}
}
