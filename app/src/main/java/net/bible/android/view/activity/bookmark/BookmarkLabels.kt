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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import java.util.*
import javax.inject.Inject

/**
 * Choose which labels to associate with a bookmark
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkLabels : ListActivityBase() {
    private var bookmarks: List<BookmarkDto>? = null
    @Inject lateinit var bookmarkControl: BookmarkControl

    private val labels: MutableList<LabelDto> = ArrayList()
    @Inject lateinit var labelDialogs: LabelDialogs

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        setContentView(R.layout.bookmark_labels)
        buildActivityComponent().inject(this)
        val bookmarkIds = intent.getLongArrayExtra(BookmarkControl.BOOKMARK_IDS_EXTRA)
        bookmarks = bookmarkControl.getBookmarksById(bookmarkIds)
        initialiseView()
    }

    private fun initialiseView() {
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        loadLabelList()
        val listArrayAdapter = BookmarkLabelItemAdapter(this, labels)
        listAdapter = listArrayAdapter
        initialiseCheckedLabels(bookmarks)
    }

    /** Finished selecting labels
     */
    fun onOkay(v: View?) {
        Log.i(TAG, "Okay clicked")
        // get the labels that are currently checked
        val selectedLabels = checkedLabels

        //associate labels with bookmarks that were passed in
        for (bookmark in bookmarks!!) {
            bookmarkControl.setBookmarkLabels(bookmark, selectedLabels)
        }
        finish()
    }

    /**
     * New Label requested
     */
    fun onNewLabel(v: View) {
        Log.i(TAG, "New label clicked")
        val newLabel = LabelDto()
        labelDialogs.createLabel(this, newLabel) {
            val selectedLabels = checkedLabels
            Log.d(TAG, "Num labels checked pre reload:" + selectedLabels.size)
            loadLabelList()
            checkedLabels = selectedLabels
            Log.d(TAG, "Num labels checked finally:" + selectedLabels.size)
        }
    }

    /** load list of docs to display
     *
     */
    private fun loadLabelList() {

        // get long book names to show in the select list
        // must clear rather than create because the adapter is linked to this specific list
        labels.clear()
        labels.addAll(bookmarkControl.assignableLabels)

        // ensure ui is updated
        notifyDataSetChanged()
    }

    /** check labels associated with the bookmark
     */
    private fun initialiseCheckedLabels(bookmarks: List<BookmarkDto>?) {
        val allCheckedLabels: MutableSet<LabelDto> = HashSet()
        for (bookmark in bookmarks!!) {
            // pre-tick any labels currently associated with the bookmark
            allCheckedLabels.addAll(bookmarkControl.getBookmarkLabels(bookmark))
        }
        checkedLabels = allCheckedLabels.toList()
    }// get selected labels// ensure ui is updated

    /**
     * set checked status of all labels
     */
    /**
     * get checked status of all labels
     */
    private var checkedLabels: List<LabelDto>
        get() {
            // get selected labels
            val listView = listView
            val checkedLabels: MutableList<LabelDto> = ArrayList()
            for (i in labels.indices) {
                if (listView.isItemChecked(i)) {
                    val label = labels[i]
                    checkedLabels.add(label)
                    Log.d(TAG, "Selected " + label.name)
                }
            }
            return checkedLabels
        }
        set(labelsToCheck) {
            for (i in labels.indices) {
                if (labelsToCheck.contains(labels[i])) {
                    listView.setItemChecked(i, true)
                } else {
                    listView.setItemChecked(i, false)
                }
            }

            // ensure ui is updated
            notifyDataSetChanged()
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.bookmark_labels_actionbar_menu, menu)
        return true
    }

    /**
     * on Click handlers
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false
        when (item.itemId) {
            R.id.manageLabels -> {
                isHandled = true
                val intent = Intent(this, ManageLabels::class.java)
                startActivityForResult(intent, IntentHelper.REFRESH_DISPLAY_ON_FINISH)
            }
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    @SuppressLint("MissingSuperCall")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Restoring state after return from label editing")
        if (requestCode == IntentHelper.REFRESH_DISPLAY_ON_FINISH) {
            // find checked labels prior to refresh
            val selectedLabels = checkedLabels

            // reload labels with new and/or amended labels
            loadLabelList()

            // re-check labels as they were before leaving this screen
            checkedLabels = selectedLabels
        }
    }

    companion object {
        private const val TAG = "BookmarkLabels"

        // this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_multiple_choice
    }
}
