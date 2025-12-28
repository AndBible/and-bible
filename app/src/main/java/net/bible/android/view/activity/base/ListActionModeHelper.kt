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
package net.bible.android.view.activity.base

import android.widget.AdapterView.OnItemClickListener
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import androidx.appcompat.view.ActionMode
import java.util.ArrayList

/**
 * Assists ListViews with action mode
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ListActionModeHelper(private val list: ListView, private val actionModeMenuResource: Int, private val singleChoice: Boolean = false) {
    private var actionMode: ActionMode? = null
    private var previousOnItemClickListener: OnItemClickListener? = null
    var isInActionMode = false
        private set

    fun startActionMode(activity: ActionModeActivity, position: Int): Boolean {
        isInActionMode = true
        if(!singleChoice)
            list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        list.setItemChecked(position, true)
        list.isLongClickable = false
        actionMode = activity.startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val inflater = mode.menuInflater
                inflater?.inflate(actionModeMenuResource, menu)
                activity.onPrepareActionMode(mode, menu, selecteditemPositions)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val selectedItemPositions = selecteditemPositions
                actionMode!!.finish()
                return activity.onActionItemClicked(item, selectedItemPositions)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                if (actionMode != null) {
                    isInActionMode = false
                    actionMode = null
                    list.isLongClickable = true

                    // remove clicklistener added at start of action mode
                    list.onItemClickListener = previousOnItemClickListener
                    previousOnItemClickListener = null
                    list.clearChoices()
                    list.requestLayout()

                    // Need to delay reset of choicemode otherwise clearChoices is optimised out.
                    // see: http://stackoverflow.com/questions/9754170/listview-selection-remains-persistent-after-exiting-choice-mode
                    if(!singleChoice)
                        list.post { list.choiceMode = ListView.CHOICE_MODE_NONE }
                }
            }
        })

        // going to overwrite the previous listener, save it so it can be restored when action mode ends.
        previousOnItemClickListener = list.onItemClickListener
        list.onItemClickListener = OnItemClickListener { parent, view, position, id -> // double check Action Mode is in operation
            if (isInActionMode) {
                if (selecteditemPositions.size == 0) {
                    actionMode!!.finish()
                }
            }
        }
        return true
    }

    /**
     * Force action mode to exit e.g. due to list view change
     */
    fun exitActionMode() {
        if (isInActionMode) {
            actionMode!!.finish()
        }
    }

    private val selecteditemPositions: List<Int>
        get() {
            val positionStates = list.checkedItemPositions
            val selectedItemPositions: MutableList<Int> = ArrayList()
            for (i in 0 until positionStates.size()) {
                val position = positionStates.keyAt(i)
                if (positionStates[position]) {
                    selectedItemPositions.add(position)
                }
            }
            return selectedItemPositions
        }

    interface ActionModeActivity {
        fun startSupportActionMode(callback: ActionMode.Callback): ActionMode?
        fun onActionItemClicked(item: MenuItem, selectedItemPositions: List<Int>): Boolean
        fun isItemChecked(position: Int): Boolean
        open fun onPrepareActionMode(mode: ActionMode, menu: Menu, selectedItemPositions: List<Int>): Boolean {
            return false
        }
    }

    companion object {
        private const val TAG = "ActionModeHelper"
    }
}
