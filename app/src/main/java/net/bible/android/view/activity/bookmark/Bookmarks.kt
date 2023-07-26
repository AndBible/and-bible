/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.bookmark

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BookmarksBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.ListActionModeHelper
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils.settings
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.common.displayName
import net.bible.service.db.BookmarksUpdatedViaSyncEvent
import java.lang.IllegalArgumentException
import java.util.*
import javax.inject.Inject

val BookmarkSortOrder.description get() =
    when(this) {
        BookmarkSortOrder.BIBLE_ORDER  -> CommonUtils.getResourceString(R.string.sort_by_bible_book)
        BookmarkSortOrder.LAST_UPDATED -> CommonUtils.getResourceString(R.string.sort_by_date)
        BookmarkSortOrder.CREATED_AT -> CommonUtils.getResourceString(R.string.sort_by_date)
        BookmarkSortOrder.CREATED_AT_DESC -> CommonUtils.getResourceString(R.string.sort_by_date)
        BookmarkSortOrder.ORDER_NUMBER -> "order number"
    }

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
    @Inject lateinit var windowControl: WindowControl

    private val labelList: MutableList<Label> = ArrayList()
    private var selectedLabelNo = 0

    // the document list
    private val bookmarkList: MutableList<BaseBookmarkWithNotes> = ArrayList()
    private var listActionModeHelper: ListActionModeHelper? = null
    override val integrateWithHistoryManager: Boolean = true

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ABEventBus.register(this)
        binding = BookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings.setLong("bookmarks-last-used", System.currentTimeMillis())
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

    override fun onDestroy() {
        super.onDestroy()
        ABEventBus.unregister(this)
    }

    fun onEventMainThread(e: BookmarksUpdatedViaSyncEvent) {
        recreate()
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
        val bookmarkArrayAdapter: ArrayAdapter<BaseBookmarkWithNotes> = BookmarkItemAdapter(
            this, bookmarkList, bookmarkControl, windowControl
        )
        listAdapter = bookmarkArrayAdapter
        loadBookmarkList()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            // check to see if Action Mode is in operation
            if (!listActionModeHelper!!.isInActionMode) {
                intent.putExtra("listPosition", position)
                bookmarkSelected(bookmarkList[position])
            }
        } catch (e: Exception) {
            Log.e(TAG, "document selection error", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    override val intentForHistoryList: Intent get()
    {
        Log.i(TAG, "Saving label no in History Intent")
        val intent = intent
        intent.putExtra(BookmarkControl.LABEL_NO_EXTRA, selectedLabelNo)
        return intent
    }

    private fun assignLabels(bookmarks: List<BaseBookmarkWithNotes>) = lifecycleScope.launch(Dispatchers.IO) {
        val labels = mutableSetOf<IdType>()
        for (b in bookmarks) {
            labels.addAll(bookmarkControl.labelsForBookmark(b).map { it.id })
        }

        val intent = Intent(this@Bookmarks, ManageLabels::class.java)
        intent.putExtra("data", ManageLabels.ManageLabelsData(
            mode = ManageLabels.Mode.ASSIGN,
            selectedLabels = labels
        ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON())
        val result = awaitIntent(intent)
        if(result.resultCode == RESULT_OK) {
            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
            for (b in bookmarks) {
                bookmarkControl.changeLabelsForBookmark(b, resultData.selectedLabels.toList())
            }
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
            withContext(Dispatchers.Main) {
                loadLabelList()
                loadBookmarkList()
            }
        }
    }

    private fun delete(bookmarks: List<BaseBookmarkWithNotes>) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_bookmarks, bookmarks.size))
            .setPositiveButton(R.string.yes) { _, _ ->
                for (bookmark in bookmarks) {
                    bookmarkControl.deleteBookmark(bookmark)
                }
                loadBookmarkList()
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(true)
            .show()
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
    private fun loadBookmarkList() = lifecycleScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            binding.empty.visibility = View.GONE
            binding.list.visibility = View.GONE
            binding.loadingIndicator.visibility = View.VISIBLE
        }
        try {
            if (selectedLabelNo > -1 && selectedLabelNo < labelList.size) {
                Log.i(TAG, "filtering bookmarks")
                val selectedLabel = labelList[selectedLabelNo]
                withContext(Dispatchers.Main) {
                    bookmarkList.clear()
                    bookmarkList.addAll(bookmarkControl.getBookmarksWithLabel(selectedLabel, bookmarkSortOrder))
                    bookmarkList.addAll(bookmarkControl.getGenericBookmarksWithLabel(selectedLabel))
                    notifyDataSetChanged()

                    // if in action mode then must exit because the data has changed, invalidating selections
                    listActionModeHelper!!.exitActionMode()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initialising view", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Bookmarks, getString(R.string.error) + " " + e.message, Toast.LENGTH_SHORT).show()
            }
        }
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.GONE
            binding.list.visibility = View.VISIBLE
            binding.empty.visibility = View.VISIBLE
            listView.setSelection(intent.getIntExtra("listPosition", 0))
        }
    }

    private fun bookmarkSelected(bookmark: BaseBookmarkWithNotes) {
        Log.i(TAG, "Bookmark selected:$bookmark")
        try {
            if (bookmark is BookmarkEntities.BibleBookmarkWithNotes && bookmarkControl.isSpeakBookmark(bookmark)) {
                speakControl.speakFromBookmark(bookmark)
            }
            val resultIntent = Intent(this, Bookmarks::class.java)
            when(bookmark) {
                is BookmarkEntities.BibleBookmarkWithNotes -> {
                    resultIntent.putExtra("verse", bookmark.verseRange.start.osisID)
                }
                is BookmarkEntities.GenericBookmarkWithNotes -> {
                    resultIntent.putExtra("key", bookmark.key)
                    resultIntent.putExtra("book", bookmark.book?.initials)
                    resultIntent.putExtra("ordinal", bookmark.ordinalStart)
                }
            }
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
            BookmarkSortOrder.BIBLE_ORDER -> BookmarkSortOrder.CREATED_AT_DESC
            BookmarkSortOrder.CREATED_AT_DESC -> BookmarkSortOrder.CREATED_AT            
            BookmarkSortOrder.CREATED_AT -> BookmarkSortOrder.BIBLE_ORDER
            else -> BookmarkSortOrder.CREATED_AT_DESC
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
                    Dialogs.showErrorMsg(R.string.error_occurred, e)
                }
            }
            R.id.manageLabels -> {
                isHandled = true
                lifecycleScope.launch(Dispatchers.Main) {
                    val intent = Intent(this@Bookmarks, ManageLabels::class.java)
                    intent.putExtra("data", ManageLabels.ManageLabelsData(
                        mode = ManageLabels.Mode.WORKSPACE,
                    ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON())
                    val result = awaitIntent(intent)
                    if(result.resultCode == RESULT_OK) {
                        val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                        windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
                        withContext(Dispatchers.Main) {
                            loadLabelList()
                            loadBookmarkList()
                        }
                    }
                }
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

    private fun getSelectedBookmarks(selectedItemPositions: List<Int>): List<BaseBookmarkWithNotes> {
        val selectedBookmarks: MutableList<BaseBookmarkWithNotes> = ArrayList()
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
