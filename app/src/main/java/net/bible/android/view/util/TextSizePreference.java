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

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import android.content.Context;
import android.util.AttributeSet;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TextSizePreference extends SeekBarPreference {

	// do not allow text smaller than 6sp;
	// there is no android:min attribute we can put in the xml definitions so easiest way is to set it here
	private static final int MIN_TEXT_SIZE = 6;
	
	@SuppressWarnings("unused")
	private static final String TAG = "TextSizePreference";
	
	public TextSizePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setMin(MIN_TEXT_SIZE);
		setDialogMessage(CommonUtils.getResourceString(R.string.prefs_text_size_sample_text));
	}
	
    protected void updateScreenValue(int value) {
    	super.updateScreenValue(value);

		getDialogMessageView().setTextSize(value);
    }
}
