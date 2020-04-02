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
package net.bible.android.view.util.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout

/**
 *
 * A view group with two children, intended for use in ListViews. This item has two
 * [TextViews][android.widget.TextView] elements (or subclasses) with the ID values
 * [text1][android.R.id.text1]
 * and [text2][android.R.id.text2]. There is an optional third View element with the
 * ID [selectedIcon][android.R.id.selectedIcon], which can be any View subclass
 * (though it is typically a graphic View, such as [ImageView][android.widget.ImageView])
 * that can be displayed when a TwoLineListItem has focus. Android supplies a
 * [standard layout resource for TwoLineListView][android.R.layout.two_line_list_item]
 * (which does not include a selected item icon), but you can design your own custom XML
 * layout for this object.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class TwoLineListItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : RelativeLayout(context, attrs, defStyleAttr) {
    override fun getAccessibilityClassName(): CharSequence {
        return TwoLineListItem::class.java.name
    }
}
