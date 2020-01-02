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
import android.graphics.Color;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.view.activity.base.ListActionModeHelper;
import net.bible.android.view.util.widget.BookmarkListItem;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDto;

import org.crosswire.jsword.book.Book;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkItemAdapter extends ArrayAdapter<BookmarkDto> {

	private int resource;
	private final ListActionModeHelper.ActionModeActivity actionModeActivity;
	private BookmarkControl bookmarkControl;

	private static int ACTIVATED_COLOUR = CommonUtils.INSTANCE.getResourceColor(R.color.list_item_activated);
	
	private static final String TAG = "BookmarkItemAdapter";

	public BookmarkItemAdapter(Context _context, int _resource, List<BookmarkDto> _items, ListActionModeHelper.ActionModeActivity actionModeActivity, BookmarkControl bookmarkControl) {
		super(_context, _resource, _items);
		resource = _resource;
		this.bookmarkControl = bookmarkControl;
		this.actionModeActivity = actionModeActivity;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		BookmarkDto item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		BookmarkListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (BookmarkListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (BookmarkListItem) convertView;
		}
		boolean isSpeak = bookmarkControl.isSpeakBookmark(item);
		if(isSpeak){
			view.getSpeakIcon().setVisibility(View.VISIBLE);
		}
		else {
			view.getSpeakIcon().setVisibility(View.GONE);
		}

		// Set value for the first text field
		if (view.getVerseText() != null) {
			String key = bookmarkControl.getBookmarkVerseKey(item);
			Book book = item.getSpeakBook();
			if(isSpeak && book != null) {
				view.getVerseText().setText(key + " (" + book.getAbbreviation() + ")");
			}else {
				view.getVerseText().setText(key);
			}
		}

		// Set value for the date text field
		if (view.getDateText() != null) {
			if (item.getCreatedOn() != null) {
				String sDt = DateFormat.format("yyyy-MM-dd HH:mm", item.getCreatedOn()).toString();
				view.getDateText().setText(sDt);
			}
		}

		// set value for the second text field
		if (view.getVerseContentText() != null) {
			try {
				String verseText = bookmarkControl.getBookmarkVerseText(item);
				view.getVerseContentText().setText(verseText);
			} catch (Exception e) {
				Log.e(TAG, "Error loading label verse text", e);
				view.getVerseContentText().setText("");
			}
		}
		return view;
	}
}
