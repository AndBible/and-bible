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
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.bible.android.activity.R;

/**
 * <p>A view group with two children, intended for use in ListViews. This item has two 
 * {@link android.widget.TextView TextViews} elements (or subclasses) with the ID values 
 * {@link android.R.id#text1 text1}
 * and {@link android.R.id#text2 text2}. There is an optional third View element with the 
 * ID {@link android.R.id#selectedIcon selectedIcon}, which can be any View subclass 
 * (though it is typically a graphic View, such as {@link android.widget.ImageView ImageView})
 * that can be displayed when a TwoLineListItem has focus. Android supplies a 
 * {@link android.R.layout#two_line_list_item standard layout resource for TwoLineListView} 
 * (which does not include a selected item icon), but you can design your own custom XML
 * layout for this object.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TwoLineListItem extends RelativeLayout {

    private TextView mText1;
    private TextView mText2;

    public TwoLineListItem(Context context) {
        this(context, null, 0);
    }

    public TwoLineListItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0); 
    }

    public TwoLineListItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

//        final TypedArray a = context.obtainStyledAttributes(
//                attrs, R.styleable.TwoLineListItem);
//
//        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        mText1 = (TextView) findViewById(R.id.text1);
        mText2 = (TextView) findViewById(R.id.text2);
    }
    
    /**
     * Returns a handle to the item with ID text1.
     * @return A handle to the item with ID text1.
     */
    public TextView getText1() {
        return mText1;
    }
    
    /**
     * Returns a handle to the item with ID text2.
     * @return A handle to the item with ID text2.
     */
    public TextView getText2() {
        return mText2;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TwoLineListItem.class.getName();
    }
}
