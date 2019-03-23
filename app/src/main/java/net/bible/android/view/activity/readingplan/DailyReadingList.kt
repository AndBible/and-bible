/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.readingplan

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView

import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.db.readingplan.ReadingPlanDBAdapter
import net.bible.service.db.readingplan.ReadingPlanOneDayDB
import net.bible.service.readingplan.event.ReadingPlanDayChangeEvent

import javax.inject.Inject

/** show a history list and allow to go to history item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReadingList : ListActivityBase() {

    @Inject lateinit var readingPlanControl: ReadingPlanControl
    private val dbAdapter = ReadingPlanDBAdapter()

    private lateinit var readingsList: List<ReadingPlanOneDayDB>
    private lateinit var adapter: ArrayAdapter<ReadingPlanOneDayDB>

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        Log.i(TAG, "Displaying General Book Key chooser")
        setContentView(R.layout.list)

        buildActivityComponent().inject(this)

		readingsList = dbAdapter.getMetaPlanDaysList(dbAdapter.metaCurrentActiveReadingPlanID!!)

        adapter = DailyReadingItemAdapter(this, android.R.layout.simple_list_item_2, readingsList)
        listAdapter = adapter

        listView.isFastScrollEnabled = true

        Log.d(TAG, "Finished displaying Search view")
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            itemSelected(readingsList[position])
        } catch (e: Exception) {
            Log.e(TAG, "Selection error", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }

    }

    private fun itemSelected(readingPlanOneDayParam: ReadingPlanOneDayDB) {
        Log.d(TAG, "Day selected:${readingPlanOneDayParam.dayNumber}")
        try {
            ABEventBus.getDefault().post(ReadingPlanDayChangeEvent(readingPlanOneDayParam))
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "error on select of gen book key", e)
        }

    }

    companion object {

        private val TAG = "DailyReadingList"
    }
}
