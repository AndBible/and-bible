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
package net.bible.android.view.activity.bookmark

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.ListActivityBase
import java.util.*
import javax.inject.Inject

/**
 * Choose a bible or commentary to use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ManageLabels : ListActivityBase() {
    private val labels: MutableList<BookmarkEntities.Label?> = ArrayList()
    private var bookmarkControl: BookmarkControl? = null
    private var labelDialogs: LabelDialogs? = null

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        setContentView(R.layout.manage_labels)
        super.buildActivityComponent().inject(this)
        initialiseView()
    }

    private fun initialiseView() {
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        loadLabelList()

        // prepare the document list view
        listAdapter = ManageLabelItemAdapter(this, LIST_ITEM_TYPE, labels, this)
    }

    fun delete(label: BookmarkEntities.Label?) {
        // delete label from db
        bookmarkControl!!.deleteLabel(label!!)

        // now refetch the list of labels
        loadLabelList()
    }

    /**
     * New Label requested
     */
    fun onNewLabel(v: View?) {
        Log.i(TAG, "New label clicked")
        val newLabel = BookmarkEntities.Label()
        labelDialogs!!.createLabel(this, newLabel) { loadLabelList() }
    }

    /**
     * New Label requested
     */
    fun editLabel(label: BookmarkEntities.Label?) {
        Log.i(TAG, "Edit label clicked")
        labelDialogs!!.editLabel(this, label!!) { loadLabelList() }
    }

    /** Finished editing labels
     */
    fun onOkay(v: View?) {
        finish()
    }

    /** load list of docs to display
     *
     */
    private fun loadLabelList() {

        // get long book names to show in the select list
        // must clear rather than create because the adapter is linked to this specific list
        labels.clear()
        labels.addAll(bookmarkControl!!.assignableLabels)

        // ensure ui is updated
        notifyDataSetChanged()
    }

    @Inject
    fun setBookmarkControl(bookmarkControl: BookmarkControl?) {
        this.bookmarkControl = bookmarkControl
    }

    @Inject
    fun setLabelDialogs(labelDialogs: LabelDialogs?) {
        this.labelDialogs = labelDialogs
    }

    companion object {
        private const val TAG = "BookmarkLabels"

        // this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
        private const val LIST_ITEM_TYPE = R.layout.manage_labels_list_item
    }
}
