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

import android.database.DataSetObserver
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.WrapperListAdapter
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils.getResourceString

/**
 * Allow selection of default Bookmark colour preference.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class ListPrefWrapperAdapter(private val mOrigAdapter: ListAdapter) : WrapperListAdapter {
    private val sampleText = getResourceString(R.string.prefs_text_size_sample_text)
    override fun getWrappedAdapter(): ListAdapter {
        return mOrigAdapter
    }

    override fun areAllItemsEnabled(): Boolean {
        return wrappedAdapter.areAllItemsEnabled()
    }

    override fun isEnabled(position: Int): Boolean {
        return wrappedAdapter.isEnabled(position)
    }

    override fun registerDataSetObserver(observer: DataSetObserver) {
        wrappedAdapter.registerDataSetObserver(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver) {
        wrappedAdapter.unregisterDataSetObserver(observer)
    }

    override fun getCount(): Int {
        return wrappedAdapter.count
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return wrappedAdapter.getView(position, convertView, parent)
    }

    override fun getItem(position: Int): Any {
        return wrappedAdapter.getItem(position)
    }

    override fun getItemId(position: Int): Long {
        return wrappedAdapter.getItemId(position)
    }

    override fun hasStableIds(): Boolean {
        return wrappedAdapter.hasStableIds()
    }

    override fun getItemViewType(position: Int): Int {
        return wrappedAdapter.getItemViewType(position)
    }

    override fun getViewTypeCount(): Int {
        return wrappedAdapter.viewTypeCount
    }

    override fun isEmpty(): Boolean {
        return wrappedAdapter.isEmpty
    }

}
