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
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.bible.android.activity.R;


public class BookmarkListItem extends RelativeLayout {

    private TextView verseText;
    private TextView verseContentText;
    private TextView dateText;
    private ImageView speakIcon;

    public BookmarkListItem(Context context) {
        this(context, null, 0);
    }

    public BookmarkListItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BookmarkListItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        verseText = findViewById(R.id.verseText);
        verseContentText = findViewById(R.id.verseContentText);
        dateText = findViewById(R.id.dateText);
        speakIcon = findViewById(R.id.speakIcon);
    }
    
    /**
     * Returns a handle to the item with ID text1.
     * @return A handle to the item with ID text1.
     */
    public TextView getVerseText() {
        return verseText;
    }
    
    /**
     * Returns a handle to the item with ID text2.
     * @return A handle to the item with ID text2.
     */
    public TextView getVerseContentText() {
        return verseContentText;
    }

    public TextView getDateText() {
        return dateText;
    }
    @Override
    public CharSequence getAccessibilityClassName() {
        return BookmarkListItem.class.getName();
    }

    public ImageView getSpeakIcon() {
        return speakIcon;
    }
}
