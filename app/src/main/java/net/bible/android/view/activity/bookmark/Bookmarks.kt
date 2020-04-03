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
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import kotlinx.android.synthetic.main.bookmarks.*
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import java.util.*
import javax.inject.Inject

/**
 * Choose Document (Book) to download
 *
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Bookmarks : ListActivityBase(), ActionModeActivity {
    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var speakControl: SpeakControl

    // language spinner
    private val labelList: MutableList<LabelDto> = ArrayList()
    private var selectedLabelNo = 0
    private var labelArrayAdapter: ArrayAdapter<LabelDto>? = null

    // the document list
    private val bookmarkList: MutableList<BookmarkDto> = ArrayList()
    private var listActionModeHelper: ListActionModeHelper? = null

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        setContentView(R.layout.bookmarks)
        sharedPreferences.edit().putLong("bookmarks-last-used", System.currentTimeMillis()).apply()
        buildActivityComponent().inject(this)

        // if coming Back using History then the LabelNo will be in the intent allowing the correct label to be pre-selected
        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey(BookmarkControl.LABEL_NO_EXTRA)) {
                val labelNo = extras.getInt(BookmarkControl.LABEL_NO_EXTRA)
                if (labelNo >= 0) {
                    selectedLabelNo = labelNo
                }
            }
        }
        initialiseView()
    }

    private fun initialiseView() {
        listActionModeHelper = ListActionModeHelper(listView, R.menu.bookmark_context_menu)
        listView.onItemLongClickListener = OnItemLongClickListener { parent, view, position, id -> listActionModeHelper!!.startActionMode(this@Bookmarks, position) }

        //prepare the Label spinner
        loadLabelList()
        labelArrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labelList)
        labelArrayAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        labelSpinner.adapter = labelArrayAdapter

        // check for pre-selected label e.g. when returning via History using Back button
        if (selectedLabelNo >= 0 && selectedLabelNo < labelList.size) {
            labelSpinner.setSelection(selectedLabelNo)
        }
        labelSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLabelNo = position
                loadBookmarkList()
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }
        loadBookmarkList()

        // prepare the document list view
        val bookmarkArrayAdapter: ArrayAdapter<BookmarkDto> = BookmarkItemAdapter(this, bookmarkList, bookmarkControl)
        listAdapter = bookmarkArrayAdapter
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            // check to see if Action Mode is in operation
            if (!listActionModeHelper!!.isInActionMode) {
                bookmarkSelected(bookmarkList[position])
            }
        } catch (e: Exception) {
            Log.e(TAG, "document selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    @SuppressLint("MissingSuperCall")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Restoring state after return from label editing")
        // the bookmarkLabels activity may have added/deleted labels or changed the bookmarks with the current label
        val prevLabel = labelList[selectedLabelNo]

        // reload labels
        loadLabelList()
        val prevLabelPos = labelList.indexOf(prevLabel)
        selectedLabelNo = if (prevLabelPos >= 0) {
            prevLabelPos
        } else {
            // this should be 'All'
            0
        }
        labelSpinner.setSelection(selectedLabelNo)

        // the label may have been renamed so cause the list to update it's text
        labelArrayAdapter!!.notifyDataSetChanged()
        loadBookmarkList()
    }

    /** allow activity to enhance intent to correctly restore state  */
    override fun getIntentForHistoryList(): Intent {
        Log.d(TAG, "Saving label no in History Intent")
        val intent = intent
        intent.putExtra(BookmarkControl.LABEL_NO_EXTRA, selectedLabelNo)
        return intent
    }

    private fun assignLabels(bookmarks: List<BookmarkDto>) {
        val bookmarkIds = LongArray(bookmarks.size)
        for (i in bookmarks.indices) {
            bookmarkIds[i] = bookmarks[i].id!!
        }
        val intent = Intent(this, BookmarkLabels::class.java)
        intent.putExtra(BookmarkControl.BOOKMARK_IDS_EXTRA, bookmarkIds)
        startActivityForResult(intent, 1)
    }

    private fun delete(bookmarks: List<BookmarkDto>) {
        for (bookmark in bookmarks) {
            bookmarkControl.deleteBookmark(bookmark, false)
        }
        loadBookmarkList()
    }

    private fun loadLabelList() {
        labelList.clear()
        labelList.addAll(bookmarkControl.allLabels)
    }

    /** a spinner has changed so refilter the doc list
     */
    private fun loadBookmarkList() {
        try {
            if (selectedLabelNo > -1 && selectedLabelNo < labelList.size) {
                Log.i(TAG, "filtering bookmarks")
                val selectedLabel = labelList[selectedLabelNo]
                bookmarkList.clear()
                bookmarkList.addAll(bookmarkControl!!.getBookmarksWithLabel(selectedLabel))
                notifyDataSetChanged()

                // if in action mode then must exit because the data has changed, invalidating selections
                listActionModeHelper!!.exitActionMode()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initialising view", e)
            Toast.makeText(this, getString(R.string.error) + " " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bookmarkSelected(bookmark: BookmarkDto) {
        Log.d(TAG, "Bookmark selected:" + bookmark.verseRange)
        try {
            if (bookmarkControl!!.isSpeakBookmark(bookmark)) {
                speakControl!!.speakFromBookmark(bookmark)
            }
            val resultIntent = Intent(this, Bookmarks::class.java)
            resultIntent.putExtra("verse", bookmark.verseRange.start.osisID)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error on bookmarkSelected", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.bookmark_actionbar_menu, menu)
        return true
    }

    /**
     * on Click handlers
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false
        when (item.itemId) {
            R.id.sortByToggle -> {
                isHandled = true
                try {
                    bookmarkControl!!.changeBookmarkSortOrder()
                    val sortDesc = bookmarkControl!!.bookmarkSortOrderDescription
                    Toast.makeText(this, sortDesc, Toast.LENGTH_SHORT).show()
                    loadBookmarkList()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sorting bookmarks", e)
                    instance.showErrorMsg(R.string.error_occurred, e)
                }
            }
            R.id.manageLabels -> {
                isHandled = true
                val intent = Intent(this, ManageLabels::class.java)
                startActivityForResult(intent, 1)
            }
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    override fun onActionItemClicked(item: MenuItem, selectedItemPositions: List<Int>): Boolean {
        val selectedBookmarks = getSelectedBookmarks(selectedItemPositions)
        when (item.itemId) {
            R.id.assign_labels -> assignLabels(selectedBookmarks)
            R.id.delete -> delete(selectedBookmarks)
        }
        return true
    }

    override fun isItemChecked(position: Int): Boolean {
        return listView.isItemChecked(position)
    }

    private fun getSelectedBookmarks(selectedItemPositions: List<Int>): List<BookmarkDto> {
        val selectedBookmarks: MutableList<BookmarkDto> = ArrayList()
        for (position in selectedItemPositions) {
            selectedBookmarks.add(bookmarkList[position])
        }
        return selectedBookmarks
    }

    companion object {
        private const val TAG = "Bookmarks"
    }
}
