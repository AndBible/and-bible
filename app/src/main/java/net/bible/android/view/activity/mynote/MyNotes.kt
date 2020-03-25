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
/**
 *
 */
package net.bible.android.view.activity.mynote

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ListView
import android.widget.Toast
import net.bible.android.activity.R
import net.bible.android.control.mynote.MyNoteControl
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.db.mynote.MyNoteDto
import java.util.*
import javax.inject.Inject

/**
 * Show a list of existing User Notes and allow view/edit/delete
 *
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNotes : ListActivityBase(), ActionModeActivity {
    @Inject lateinit var myNoteControl: MyNoteControl

    // the document list
    private val myNoteList: MutableList<MyNoteDto?> = ArrayList()
    private var listActionModeHelper: ListActionModeHelper? = null

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        // integrateWithHistoryManager to ensure the previous document is loaded again when the user presses Back
        super.onCreate(savedInstanceState, true)
        setContentView(R.layout.list)
        buildActivityComponent().inject(this)
        initialiseView()
    }

    private fun initialiseView() {
        listActionModeHelper = ListActionModeHelper(listView, R.menu.usernote_context_menu)
        listView.onItemLongClickListener=
            OnItemLongClickListener {
                parent, view, position, id ->
                listActionModeHelper!!.startActionMode(this@MyNotes, position)
            }
        loadUserNoteList()

        // prepare the document list view
        val myNoteArrayAdapter = MyNoteItemAdapter(this, LIST_ITEM_TYPE, myNoteList, this, myNoteControl)
        listAdapter = myNoteArrayAdapter
        registerForContextMenu(listView)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            // check to see if Action Mode is in operation
            if (!listActionModeHelper!!.isInActionMode) {
                myNoteSelected(myNoteList[position])

                // HistoryManager will create a new Activity on Back
                val resultIntent = Intent(this, MyNotes::class.java)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "document selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    override fun onActionItemClicked(item: MenuItem, selectedItemPositions: List<Int>): Boolean {
        val selectedNotes = getSelectedMyNotes(selectedItemPositions)
        if (!selectedNotes.isEmpty()) {
            when (item.itemId) {
                R.id.delete -> {
                    delete(selectedNotes)
                    return true
                }
            }
        }
        return false
    }

    override fun isItemChecked(position: Int): Boolean {
        return listView.isItemChecked(position)
    }

    private fun getSelectedMyNotes(selectedItemPositions: List<Int>): List<MyNoteDto?> {
        val selectedNotes: MutableList<MyNoteDto?> = ArrayList()
        for (position in selectedItemPositions) {
            selectedNotes.add(myNoteList[position])
        }
        return selectedNotes
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.mynote_actionbar_menu, menu)
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
                    myNoteControl.changeSortOrder()
                    val sortDesc = myNoteControl.sortOrderDescription
                    Toast.makeText(this, sortDesc, Toast.LENGTH_SHORT).show()
                    loadUserNoteList()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sorting notes", e)
                    instance.showErrorMsg(R.string.error_occurred, e)
                }
            }
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    private fun delete(myNotes: List<MyNoteDto?>) {
        for (myNote in myNotes) {
            myNoteControl.deleteMyNote(myNote)
        }
        loadUserNoteList()
    }

    private fun loadUserNoteList() {
        // item positions will all change so exit any action mode
        listActionModeHelper!!.exitActionMode()
        myNoteList.clear()
        myNoteList.addAll(myNoteControl.allMyNotes)
        notifyDataSetChanged()
    }

    /**
     * User selected a MyNote so download it
     *
     * @param myNote
     */
    private fun myNoteSelected(myNote: MyNoteDto?) {
        Log.d(TAG, "User Note selected:" + myNote!!.verseRange)
        try {
            myNoteControl.showNoteView(myNote)
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to show note", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    companion object {
        private const val LIST_ITEM_TYPE = R.layout.list_item_2_highlighted
        private const val TAG = "UserNotes"
    }
}
