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
package net.bible.android.view.activity.base

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import net.bible.android.activity.R

/**
 * Base class for List activities.  Copied from Android source.
 * A copy of ListActivity from Android source which also extends ActionBarActivity and the And Bible Activity base class.
 *
 * ListActivity does not extend ActionBarActivity so when implementing ActionBar functionality I created this, which does.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class ListActivityBase : CustomTitlebarActivityBase {
    /**
     * This field should be made private, so it is hidden from the SDK. {@hide
     * * }
     */
    protected var mAdapter: ListAdapter? = null

    /**
     * This field should be made private, so it is hidden from the SDK. {@hide
     * * }
     */
    protected var mList: ListView? = null
    private val mHandler = Handler()
    private var mFinishedStart = false

    constructor() : super() {}
    constructor(optionsMenuId: Int) : super(optionsMenuId) {}

    protected fun notifyDataSetChanged() {
        val listAdapter = listAdapter
        if (listAdapter != null && listAdapter is ArrayAdapter<*>) {
            listAdapter.notifyDataSetChanged()
        } else {
            Log.w(TAG, "Could not update list Array Adapter")
        }
    }

    private val mRequestFocus = Runnable { mList!!.focusableViewAvailable(mList) }

    /**
     * This method will be called when an item in the list is selected.
     * Subclasses should override. Subclasses can call
     * getListView().getItemAtPosition(position) if they need to access the data
     * associated with the selected item.
     *
     * @param l
     * The ListView where the click happened
     * @param v
     * The view that was clicked within the ListView
     * @param position
     * The position of the view in the list
     * @param id
     * The row id of the item that was clicked
     */
    protected open fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {}

    /**
     * Ensures the list view has been created before Activity restores all of
     * the view states.
     *
     * @see Activity.onRestoreInstanceState
     */
    override fun onRestoreInstanceState(state: Bundle) {
        ensureList()
        super.onRestoreInstanceState(state)
    }

    /**
     * @see Activity.onDestroy
     */
    override fun onDestroy() {
        mHandler.removeCallbacks(mRequestFocus)
        super.onDestroy()
    }

    /**
     * Updates the screen state (current list and other views) when the content
     * changes.
     *
     * @see Activity.onContentChanged
     */
    //	public void onContentChanged() {
    override fun onSupportContentChanged() {
        super.onSupportContentChanged()
        val emptyView = findViewById<View>(android.R.id.empty)
        mList = findViewById<View>(android.R.id.list) as ListView
        if (mList == null) {
            throw RuntimeException("Your content must have a ListView whose id attribute is "
                + "'android.R.id.list'")
        }
        if (emptyView != null) {
            mList!!.emptyView = emptyView
        }
        mList!!.onItemClickListener = mOnClickListener
        if (mFinishedStart) {
            listAdapter = mAdapter!!
        }
        mHandler.post(mRequestFocus)
        mFinishedStart = true
    }

    /**
     * Set the currently selected list item to the specified position with the
     * adapter's data
     *
     * @param position
     */
    fun setSelection(position: Int) {
        mList!!.setSelection(position)
    }

    /**
     * Get the position of the currently selected list item.
     */
    val selectedItemPosition: Int
        get() = mList!!.selectedItemPosition

    /**
     * Get the cursor row ID of the currently selected list item.
     */
    val selectedItemId: Long
        get() = mList!!.selectedItemId

    /**
     * Get the activity's list view widget.
     */
    val listView: ListView
        get() {
            ensureList()
            return mList!!
        }
    /**
     * Get the ListAdapter associated with this activity's ListView.
     */
    /**
     * Provide the cursor for the list view.
     */
    var listAdapter: ListAdapter?
        get() = mAdapter
        set(adapter) {
            synchronized(this) {
                ensureList()
                mAdapter = adapter
                mList!!.adapter = adapter
            }
        }

    private fun ensureList() {
        if (mList != null) {
            return
        }
        setContentView(R.layout.list_content_simple)
    }

    private val mOnClickListener = OnItemClickListener { parent, v, position, id -> onListItemClick(parent as ListView, v, position, id) }

    companion object {
        private const val TAG = "ActionBarListActivity"
    }
}
