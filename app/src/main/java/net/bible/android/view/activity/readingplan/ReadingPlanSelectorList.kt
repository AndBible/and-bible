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
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast

import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.db.readingplan.ReadingPlanDBAdapter
import net.bible.service.db.readingplan.ReadingPlanInformationDB
import net.bible.service.db.readingplan.ReadingPlanOneDayDB
import net.bible.service.readingplan.event.ReadingPlanDayChangeEvent

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ReadingPlanSelectorList : ListActivityBase() {

    private val dbAdapter = ReadingPlanDBAdapter()
    private var readingPlanList: List<ReadingPlanInformationDB> = dbAdapter.getPlanList()
    private lateinit var planArrayAdapter: ArrayAdapter<ReadingPlanInformationDB>

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        Log.i(TAG, "Displaying Reading Plan List")
        setContentView(R.layout.list)

        buildActivityComponent().inject(this)
        try {
            planArrayAdapter = ReadingPlanItemAdapter(this, R.layout.reading_plan_list_single, readingPlanList)
            listAdapter = planArrayAdapter

            registerForContextMenu(listView)
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred analysing reading lists", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
            finish()
        }

        Log.d(TAG, "Finished displaying Reading Plan list")
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            dbAdapter.switchToReadingPlan(readingPlanList[position])

            val readingPlanOneDay: ReadingPlanOneDayDB? = try {
                ReadingPlanOneDayDB(readingPlanList[position])
            } catch (e: Exception) { null }

            ABEventBus.getDefault().post(ReadingPlanDayChangeEvent(readingPlanOneDay, readingPlanList[position]))

            setResult(Activity.RESULT_OK)
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "Plan selection error", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }

    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = menuInflater
        inflater.inflate(R.menu.reading_plan_list_context_menu, menu)
    }


    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val plan = planArrayAdapter.getItem(menuInfo.position)
        Log.d(TAG, "Selected " + plan!!.fileName)
        when (item.itemId) {
            R.id.deletePlan -> {
                if (!plan.isCustomUserAdded) {
                    Dialogs.getInstance().showErrorMsg(getString(R.string.error_only_delete_custom_plans))
                    return false
                }
                var isDeleted = false
                Dialogs.getInstance().showMsg(R.string.delete_plan_question, true)
                {
                    if (dbAdapter.deletePlan(plan.readingPlanID)) {
                        isDeleted = true
                        planArrayAdapter.remove(plan)
                        Toast.makeText(this, getString(R.string.plan_deleted), Toast.LENGTH_SHORT).show()
                    }
                }
                return isDeleted
            }
        }
        return false
    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = "ReadingPlanList"

    }
}
