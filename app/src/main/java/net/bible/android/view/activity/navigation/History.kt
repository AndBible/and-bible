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
package net.bible.android.view.activity.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.text.format.DateFormat.format
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.HistoryBinding
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.base.SharedActivityState.Companion.currentWorkspaceName
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import javax.inject.Inject

/** show a history list and allow to go to history item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class History : ListActivityBase() {

    private lateinit var binding: HistoryBinding

    private var mHistoryItemList: List<HistoryItem>? = null
    @Inject lateinit var historyManager: HistoryManager
    @Inject lateinit var windowControl: WindowControl
    override val customTheme: Boolean
        get() = false

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying History view")
        binding = HistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        listAdapter = createAdapter()
        val name = currentWorkspaceName
        title = getString(R.string.history_for, name, windowControl.activeWindowPosition + 1)
        Log.d(TAG, "Finished displaying Search view")
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected fun createAdapter(): ListAdapter {
        mHistoryItemList = historyManager.history
        return object : ArrayAdapter<HistoryItem>(
            this,
            LIST_ITEM_TYPE,
            R.id.titleText, mHistoryItemList!!
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

                val view = super.getView(position, convertView, parent)
                view.findViewById<TextView>(R.id.titleText).text = mHistoryItemList!![position].description.toString()
                val formattedDate = format("yyyy-MM-dd HH:mm", mHistoryItemList!![position].createdAt).toString()
                view.findViewById<TextView>(R.id.dateText).text = formattedDate

                return view

            }
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            historyItemSelected(mHistoryItemList!![position])
        } catch (e: Exception) {
            Log.e(TAG, "Selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun historyItemSelected(historyItem: HistoryItem) {
        Log.i(TAG, "chose:$historyItem")
        historyItem.revertTo()
        doFinish()
    }

    private fun doFinish() {
        val resultIntent = Intent()
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val TAG = "History"
        private const val LIST_ITEM_TYPE = R.layout.history_list_item
    }
}
