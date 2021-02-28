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
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BookmarksBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.view.activity.mynote.description
import net.bible.service.common.CommonUtils
import net.bible.service.common.displayName
import net.bible.service.sword.SwordContentFacade
import java.lang.IllegalArgumentException
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

    private lateinit var binding: BookmarksBinding

    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var swordContentFacade: SwordContentFacade
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    private val labelList: MutableList<Label> = ArrayList()
    private var selectedLabelNo = 0

    // the document list
    private val bookmarkList: MutableList<Bookmark> = ArrayList()
    private var listActionModeHelper: ListActionModeHelper? = null

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        binding = BookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
        listView.onItemLongClickListener = OnItemLongClickListener{
            _, _, position, _ -> listActionModeHelper!!.startActionMode(this@Bookmarks, position)
        }

        //prepare the Label spinner
        loadLabelList()

        // check for pre-selected label e.g. when returning via History using Back button
        if (selectedLabelNo >= 0 && selectedLabelNo < labelList.size) {
            binding.labelSpinner.setSelection(selectedLabelNo)
        }
        binding.labelSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLabelNo = position
                loadBookmarkList()
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }

        // prepare the document list view
        val bookmarkArrayAdapter: ArrayAdapter<Bookmark> = BookmarkItemAdapter(
            this, bookmarkList, bookmarkControl, swordContentFacade, activeWindowPageManagerProvider
        )
        listAdapter = bookmarkArrayAdapter
        loadBookmarkList()
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

    /** allow activity to enhance intent to correctly restore state  */
    override fun getIntentForHistoryList(): Intent {
        Log.d(TAG, "Saving label no in History Intent")
        val intent = intent
        intent.putExtra(BookmarkControl.LABEL_NO_EXTRA, selectedLabelNo)
        return intent
    }

    private fun assignLabels(bookmarks: List<Bookmark>) = GlobalScope.launch(Dispatchers.IO) {
        val labels = mutableSetOf<Long>()
        for (b in bookmarks) {
            labels.addAll(bookmarkControl.labelsForBookmark(b).map { it.id })
        }

        val intent = Intent(this@Bookmarks, ManageLabels::class.java)
        intent.putExtra(BookmarkControl.LABEL_IDS_EXTRA, labels.toLongArray())
        val result = awaitIntent(intent)
        val labelIds = result?.resultData?.extras?.getLongArray(BookmarkControl.LABEL_IDS_EXTRA)
        if(labelIds != null) {
            for (b in bookmarks) {
                bookmarkControl.changeLabelsForBookmark(b, labelIds.toList())
            }
        }
        withContext(Dispatchers.Main) {
            loadLabelList()
            loadBookmarkList()
        }
    }

    private fun delete(bookmarks: List<Bookmark>) {
        for (bookmark in bookmarks) {
            bookmarkControl.deleteBookmark(bookmark)
        }
        loadBookmarkList()
    }

    private fun loadLabelList() {
        labelList.clear()
        labelList.addAll(bookmarkControl.allLabels)
        val labelArrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labelList.map { it.displayName })
        labelArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.labelSpinner.adapter = labelArrayAdapter
    }

    /** a spinner has changed so refilter the doc list
     */
    private fun loadBookmarkList() {
        try {
            if (selectedLabelNo > -1 && selectedLabelNo < labelList.size) {
                Log.i(TAG, "filtering bookmarks")
                val selectedLabel = labelList[selectedLabelNo]
                bookmarkList.clear()
                bookmarkList.addAll(bookmarkControl.getBookmarksWithLabel(selectedLabel, bookmarkSortOrder))
                notifyDataSetChanged()

                // if in action mode then must exit because the data has changed, invalidating selections
                listActionModeHelper!!.exitActionMode()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initialising view", e)
            Toast.makeText(this, getString(R.string.error) + " " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bookmarkSelected(bookmark: Bookmark) {
        Log.d(TAG, "Bookmark selected:" + bookmark.verseRange)
        try {
            if (bookmarkControl.isSpeakBookmark(bookmark)) {
                speakControl.speakFromBookmark(bookmark)
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

    private fun changeBookmarkSortOrder() {
        bookmarkSortOrder = when (bookmarkSortOrder) {
            BookmarkSortOrder.BIBLE_ORDER -> BookmarkSortOrder.CREATED_AT
            BookmarkSortOrder.CREATED_AT -> BookmarkSortOrder.BIBLE_ORDER
            else -> BookmarkSortOrder.CREATED_AT
        }
    }

    private var bookmarkSortOrder: BookmarkSortOrder
        get() {
            val bookmarkSortOrderStr = CommonUtils.getSharedPreference(BOOKMARK_SORT_ORDER, BookmarkSortOrder.BIBLE_ORDER.toString())
            return try {
                BookmarkSortOrder.valueOf(bookmarkSortOrderStr!!)
            } catch (e: IllegalArgumentException) { BookmarkSortOrder.BIBLE_ORDER }
        }
        private set(bookmarkSortOrder) {
            CommonUtils.saveSharedPreference(BOOKMARK_SORT_ORDER, bookmarkSortOrder.toString())
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
                    changeBookmarkSortOrder()
                    val sortDesc = bookmarkSortOrder.description
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
                startActivityForResult(intent, REQUEST_MANAGE_LABELS)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == REQUEST_MANAGE_LABELS) {
            loadLabelList()
            loadBookmarkList()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun isItemChecked(position: Int): Boolean {
        return listView.isItemChecked(position)
    }

    private fun getSelectedBookmarks(selectedItemPositions: List<Int>): List<Bookmark> {
        val selectedBookmarks: MutableList<Bookmark> = ArrayList()
        for (position in selectedItemPositions) {
            selectedBookmarks.add(bookmarkList[position])
        }
        return selectedBookmarks
    }

    companion object {
        private const val BOOKMARK_SORT_ORDER = "BookmarkSortOrder"
        private const val TAG = "Bookmarks"
        private const val REQUEST_MANAGE_LABELS = 10
    }
}
