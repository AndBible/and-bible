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

package net.bible.android.view.util.widget;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.UiUtils;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.StringUtils;

/**
 * Set each list view item to represent background colour of icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkStyleAdapterHelper {

	private String sampleText = CommonUtils.INSTANCE.getResourceString(R.string.prefs_text_size_sample_text);

	public void styleView(TextView view, BookmarkStyle bookmarkStyle, Context context, boolean overrideText, boolean centreText) {
		styleView(view, bookmarkStyle, context, overrideText, centreText, false);
	}

	public void styleView(TextView view, BookmarkStyle bookmarkStyle, Context context, boolean overrideText, boolean centreText, boolean emphasize) {

		// prepare text to be shown
		String baseText;
		if (overrideText) {
			baseText = sampleText;
		} else {
			baseText = view.getText().toString();
			// avoid multiple *'s
			if (baseText.startsWith("*")) {
				StringUtils.strip(baseText, "*");
			}
		}
		if(emphasize) {
			baseText = "⤇ " + baseText + " ⤆";
		}

		int backgroundColor = Color.WHITE;
		CharSequence imgText;
		switch (bookmarkStyle) {
			case SPEAK:
				backgroundColor = UiUtils.INSTANCE.getThemeBackgroundColour(context);
				view.setTextColor(UiUtils.INSTANCE.getThemeTextColour(context));
				imgText = addImageAtStart("* "+baseText, R.drawable.hearing, context);
				view.setText(imgText, TextView.BufferType.SPANNABLE);
				break;
			case YELLOW_STAR:
				backgroundColor = UiUtils.INSTANCE.getThemeBackgroundColour(context);
				view.setTextColor(UiUtils.INSTANCE.getThemeTextColour(context));
				imgText = addImageAtStart("* "+baseText, R.drawable.goldstar16x16, context);
				view.setText(imgText, TextView.BufferType.SPANNABLE);
				break;
			case RED_HIGHLIGHT:
				backgroundColor = BookmarkStyle.RED_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
			case YELLOW_HIGHLIGHT:
				backgroundColor = BookmarkStyle.YELLOW_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
			case GREEN_HIGHLIGHT:
				backgroundColor = BookmarkStyle.GREEN_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
			case BLUE_HIGHLIGHT:
				backgroundColor = BookmarkStyle.BLUE_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
		}
		view.setBackgroundColor(backgroundColor);
		view.setHeight(CommonUtils.INSTANCE.convertDipsToPx(30));
		if (centreText) {
			view.setGravity(Gravity.CENTER);
		}
	}

	/**
	 * Replace first character of text with image
	 */
	private CharSequence addImageAtStart(String text, int drawableImage, Context context) {
		ImageSpan imageSpan = new ImageSpan(context, drawableImage, ImageSpan.ALIGN_BASELINE);
		final SpannableString spannableString = new SpannableString(text);
		spannableString.setSpan(imageSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
		return spannableString;
	}
}
